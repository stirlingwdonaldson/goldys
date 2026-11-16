import base64
import json
from pathlib import Path
from acceptance import run

fixture = r'''
import csv
from docx import Document
from openpyxl import Workbook
from reportlab.pdfgen.canvas import Canvas
from PIL import Image, ImageDraw
with open('/output/sales.csv','w') as f: f.write('product,sales\nSynthetic,42\n')
doc = Document(); doc.add_paragraph('Synthetic sales 42'); doc.save('/output/report.docx')
book = Workbook(); book.active.append(['product','sales']); book.active.append(['Synthetic',42]); book.save('/output/sales.xlsx')
pdf = Canvas('/output/report.pdf'); pdf.drawString(72,720,'Synthetic sales 42'); pdf.save()
image = Image.new('RGB',(800,200),'white'); ImageDraw.Draw(image).text((50,60),'Synthetic sales 42', fill='black', font_size=36); image.save('/output/scan.png')
print('fixtures')
'''
result = run(fixture, seconds=60)
assert result['status'] == 'COMPLETED', result
assert len(result['outputs']) == 5
validation = run(Path('/opt/goldys-agent/validate_outputs.py').read_text(), 60, result['outputs'])
assert validation['status'] == 'COMPLETED' and validation['stdout'].strip() == 'VALIDATED', validation
print('PASS five generated output formats validated')
extraction = run(Path('/opt/goldys-agent/extract.py').read_text(), 60, result['outputs'])
assert extraction['status'] == 'COMPLETED', extraction
parsed = json.loads(extraction['stdout'])
for file in parsed:
    assert file['status'] == 'EXTRACTED', file
    assert '42' in json.dumps(file), file
print('PASS synthetic CSV, XLSX, DOCX, PDF text and PNG OCR: expected value 42 in all five')
malicious = [{'name':'formula.csv','base64':base64.b64encode(b'=HYPERLINK("https://example.com")\n').decode()}]
assert run(Path('/opt/goldys-agent/validate_outputs.py').read_text(), 20, malicious)['status'] == 'FAILED'
print('PASS spreadsheet formula artifact rejection')
