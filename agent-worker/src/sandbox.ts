import { createConnection } from 'node:net';
import { readFile } from 'node:fs/promises';
import type { SpringJobs, Claim } from './jobs';
import { Type } from 'typebox';
import type { ToolDefinition } from '@earendil-works/pi-coding-agent';

export interface SandboxInput { name: string; base64: string }
export interface SandboxResult {
  status: 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'LIMIT_EXCEEDED' | 'REJECTED' | 'BUSY';
  stdout?: string;
  outputs?: SandboxInput[];
  notice?: string;
}

/** Fixed local broker. Aborting closes the connection; broker kills the whole container. */
export function executeSandbox(code: string, inputs: SandboxInput[], signal?: AbortSignal): Promise<SandboxResult> {
  signal?.throwIfAborted();
  const body = Buffer.from(JSON.stringify({ code, inputs, seconds: 60 }));
  if (body.length > 140 * 1024 * 1024) throw new Error('Sandbox request exceeds limit');
  return new Promise((resolve, reject) => {
    const socket = createConnection('/run/goldys-sandbox/broker.sock');
    const chunks: Buffer[] = [];
    let size = 0;
    const abort = () => socket.destroy(new Error('Sandbox execution cancelled'));
    signal?.addEventListener('abort', abort, { once: true });
    socket.setTimeout(75000, abort);
    socket.on('connect', () => { socket.write(`${body.length}\n`); socket.write(body); });
    socket.on('data', chunk => {
      size += chunk.length;
      if (size > 140 * 1024 * 1024) { socket.destroy(new Error('Sandbox response exceeds limit')); return; }
      chunks.push(Buffer.from(chunk));
    });
    socket.on('error', reject);
    socket.on('close', () => signal?.removeEventListener('abort', abort));
    socket.on('end', () => {
      try {
        const result = JSON.parse(Buffer.concat(chunks).toString()) as SandboxResult;
        if (!['COMPLETED', 'FAILED', 'CANCELLED', 'LIMIT_EXCEEDED', 'REJECTED', 'BUSY'].includes(result.status)) {
          throw new Error('Invalid sandbox response');
        }
        resolve(result);
      } catch { reject(new Error('Invalid sandbox response')); }
      finally { socket.destroy(); }
    });
    if (signal?.aborted) abort();
  });
}

export function workspaceAnalysisTools(host: SpringJobs, claim: Claim): ToolDefinition[] {
  const authorize = () => host.heartbeat(claim);
  const inputs = async (ids?: string[]) => (await host.files(claim, ids)).map(file => ({ name: file.info.name, base64: file.bytes }));
  const result = (details: unknown) => ({ content: [{ type: 'text' as const, text: JSON.stringify(details) }], details });
  const fileIds = Type.Optional(Type.Array(Type.String({ format: 'uuid' }), { maxItems: 5 }));
  return [{
    name: 'goldys_list_files', label: 'List private files', description: 'List retained private files and IDs. Select at most five IDs when more files exist. Files are staged at /inputs/<name>.',
    parameters: Type.Object({}),
    execute: async (_id, _args, signal) => { await authorize(); signal?.throwIfAborted(); return result(await host.fileList(claim)); },
  }, {
    name: 'goldys_export_sales_dataset', label: 'Export governed daily sales',
    description: 'Export daily governed gross/net/GST for 1–31 days to a private CSV. Values and missing cells come directly from Spring metric semantics. The CSV becomes available in /inputs for subsequent offline analysis. Other datasets are unsupported.',
    parameters: Type.Object({ from: Type.String({ pattern: '^\\d{4}-\\d{2}-\\d{2}$' }), to: Type.String({ pattern: '^\\d{4}-\\d{2}-\\d{2}$' }) }),
    execute: async (_id, args, signal) => {
      await authorize(); signal?.throwIfAborted();
      const range = args as { from: string; to: string };
      const dataset = await host.salesDataset(claim, range.from, range.to);
      await authorize(); signal?.throwIfAborted();
      return result(dataset);
    },
  }, {
    name: 'goldys_extract_files', label: 'Read uploaded files',
    description: 'Extract bounded text, tables and OCR from private uploads. Returns explicit partial and uncertain extraction. Files do not become authoritative Goldys records.',
    parameters: Type.Object({ fileIds }),
    execute: async (_id, args, signal) => {
      await authorize(); signal?.throwIfAborted();
      const files = await inputs((args as { fileIds?: string[] }).fileIds);
      if (!files.length) return result({ status: 'MISSING', notice: 'No retained uploads in this investigation.' });
      const code = await readFile('/opt/goldys-agent/extract.py', 'utf8');
      const extracted = await executeSandbox(code, files, signal);
      await authorize(); signal?.throwIfAborted();
      return result({ status: extracted.status, extraction: extracted.stdout ?? '', notice: extracted.notice });
    },
  }, {
    name: 'goldys_analyze_python', label: 'Analyze with Python',
    description: 'Run bounded offline Python. Uploaded files are in /inputs. Write PDF, PNG, DOCX, XLSX or CSV outputs to /output. No database, internet or credentials. Exploratory calculations never replace governed metrics. Explain inputs, formulas and limits.',
    parameters: Type.Object({ code: Type.String({ minLength: 1, maxLength: 65536 }), fileIds }),
    execute: async (_id, args, signal) => {
      await authorize(); signal?.throwIfAborted();
      const execution = await executeSandbox((args as { code: string }).code, await inputs((args as { fileIds?: string[] }).fileIds), signal);
      await authorize(); signal?.throwIfAborted();
      const artifacts: unknown[] = [];
      if (execution.status === 'COMPLETED' && execution.outputs?.length) {
        const validator = await readFile('/opt/goldys-agent/validate_outputs.py', 'utf8');
        const checked = await executeSandbox(validator, execution.outputs, signal);
        if (checked.status !== 'COMPLETED' || checked.stdout?.trim() !== 'VALIDATED') {
          return result({ status: 'REJECTED', stdout: execution.stdout, notice: 'Generated files failed format or active-content checks. No downloads were registered.' });
        }
        for (const output of execution.outputs) {
          await authorize(); signal?.throwIfAborted();
          artifacts.push(await host.artifact(claim, output.name, output.base64));
        }
      }
      await authorize(); signal?.throwIfAborted();
      return result({ status: execution.status, stdout: execution.stdout ?? '', artifacts, notice: execution.notice });
    },
  }];
}
