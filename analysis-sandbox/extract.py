"""Fixed bounded extraction, executed only inside the offline analysis sandbox."""
import csv
import io
import json
from pathlib import Path
import subprocess
import time
import zipfile

MAX_ROWS = 100000
MAX_PAGES = 100
MAX_PREVIEW = 12000
DEADLINE = time.monotonic() + 45


def office_archive(path):
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist()
        if len(members) > 5000 or sum(item.file_size for item in members) > 100 * 1024 * 1024:
            raise ValueError('Office archive exceeds expanded-size limit')
        for item in members:
            name = item.filename.lower()
            if item.flag_bits & 1 or any(part in name for part in ('vbaproject', 'embeddings/', 'externallinks/')):
                raise ValueError('Encrypted, macro, embedded-object or external-link Office files are unsupported')
            if name.endswith('.rels') and b'TargetMode="External"' in archive.read(item):
                raise ValueError('External Office relationships are unsupported; export a values-only file')


def tabular(rows, limit=MAX_ROWS):
    count = 0
    preview = []
    size = 0
    partial = False
    for row in rows:
        if count >= limit or time.monotonic() >= DEADLINE:
            partial = True
            break
        count += 1
        text = json.dumps(list(row), default=str, ensure_ascii=False)
        if size + len(text) < MAX_PREVIEW:
            preview.append(text)
            size += len(text)
        else:
            partial = True
    return {'rowsRead': count, 'partial': partial, 'preview': '\n'.join(preview),
            'notice': 'Preview is bounded; empty cells remain missing. No formula is evaluated.'}


def ocr(path):
    output = subprocess.run(['tesseract', str(path), 'stdout'], capture_output=True,
                            timeout=max(1, min(10, DEADLINE - time.monotonic())), check=True)
    return output.stdout.decode('utf-8', errors='replace')[:MAX_PREVIEW]


def extract(path):
    extension = path.suffix.lower()
    if extension in ('.txt', '.csv', '.tsv'):
        with path.open(encoding='utf-8-sig', errors='strict', newline='') as stream:
            if extension == '.txt':
                text = stream.read(MAX_PREVIEW + 1)
                return {'preview': text[:MAX_PREVIEW], 'partial': len(text) > MAX_PREVIEW}
            csv.field_size_limit(1024 * 1024)
            return tabular(csv.reader(stream, delimiter='\t' if extension == '.tsv' else ','))
    if extension in ('.xlsx', '.docx'):
        office_archive(path)
    if extension == '.xlsx':
        from openpyxl import load_workbook
        workbook = load_workbook(path, read_only=True, data_only=False, keep_links=False)
        try:
            sheets = []
            remaining = MAX_ROWS
            for sheet in workbook:
                if len(sheets) >= 20 or remaining <= 0 or time.monotonic() >= DEADLINE:
                    return {'sheets': sheets, 'partial': True, 'notice': 'Workbook limit reached'}
                def rows():
                    for index, row in enumerate(sheet.iter_rows()):
                        yield [('[FORMULA NOT EVALUATED]' if cell.data_type == 'f' else cell.value) for cell in row]
                result = tabular(rows(), remaining)
                remaining -= result['rowsRead']
                sheets.append({'sheet': sheet.title, **result})
            return {'sheets': sheets, 'partial': any(sheet['partial'] for sheet in sheets)}
        finally:
            workbook.close()
    if extension == '.docx':
        from docx import Document
        document = Document(path)
        text = '\n'.join(paragraph.text for paragraph in document.paragraphs)
        text += '\n' + '\n'.join('\t'.join(cell.text for cell in row.cells)
                                  for table in document.tables for row in table.rows)
        return {'preview': text[:MAX_PREVIEW], 'partial': len(text) > MAX_PREVIEW,
                'notice': 'Text and tables only; embedded images and layout are not transcribed.'}
    if extension in ('.png', '.jpg', '.jpeg'):
        from PIL import Image
        Image.MAX_IMAGE_PIXELS = 20000000
        with Image.open(path) as image:
            if image.width * image.height > 20000000:
                raise ValueError('Image pixel limit exceeded')
            image.verify()
        return {'preview': ocr(path), 'partial': True,
                'notice': 'OCR is uncertain; verify extracted figures against the original image.'}
    if extension == '.pdf':
        from pypdf import PdfReader
        reader = PdfReader(path)
        if reader.is_encrypted:
            raise ValueError('Password-protected PDF is unsupported')
        pages = []
        for index, page in enumerate(reader.pages):
            if index >= MAX_PAGES or time.monotonic() >= DEADLINE:
                break
            text = page.extract_text() or ''
            method = 'TEXT'
            if not text.strip():
                target = '/tmp/ocr-page'
                subprocess.run(['pdftoppm', '-f', str(index + 1), '-l', str(index + 1),
                                '-scale-to', '2400', '-singlefile', '-png', str(path), target],
                               check=True, capture_output=True, timeout=10)
                text = ocr(Path(target + '.png'))
                method = 'OCR_UNCERTAIN'
            pages.append({'page': index + 1, 'preview': text[:2000], 'method': method, 'partial': len(text) > 2000})
            if sum(len(page['preview']) for page in pages) >= MAX_PREVIEW:
                break
        return {'pages': pages, 'totalPages': len(reader.pages),
                'partial': len(pages) < len(reader.pages) or any(page['partial'] or page['method'] == 'OCR_UNCERTAIN' for page in pages),
                'notice': 'Bounded extraction; OCR and layout may be incomplete. Verify figures against original pages.'}
    raise ValueError('Unsupported format; convert to PDF, PNG/JPEG, DOCX, XLSX, UTF-8 CSV/TSV or text')


def main():
    results = []
    for path in Path('/inputs').iterdir():
        try:
            results.append({'name': path.name, 'status': 'EXTRACTED', **extract(path)})
        except Exception:
            results.append({'name': path.name, 'status': 'FAILED',
                            'notice': 'Extraction failed or a format/resource safety limit was reached. Convert to a simpler supported file.'})
    # Keep the entire structured response valid within the broker's stdout limit.
    def shorten(value):
        if isinstance(value, dict):
            if isinstance(value.get('preview'), str) and value['preview']:
                value['preview'] = value['preview'][:len(value['preview']) // 2]
                value['partial'] = True
            for child in value.values():
                shorten(child)
        elif isinstance(value, list):
            for child in value:
                shorten(child)
    while len(json.dumps(results).encode()) > 60000:
        shorten(results)
        for result in results:
            result['partial'] = True
    encoded = json.dumps(results)
    Path('/output/extraction.json').write_text(encoded)
    print(encoded)


if __name__ == '__main__':
    main()
