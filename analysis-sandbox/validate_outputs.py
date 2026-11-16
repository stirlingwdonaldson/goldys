"""Fixed artifact validation in a fresh sandbox, never in the trusted supervisor."""
import csv
from pathlib import Path
import re
import zipfile


def office(path):
    with zipfile.ZipFile(path) as archive:
        items = archive.infolist()
        assert len(items) <= 5000 and sum(item.file_size for item in items) <= 100 * 1024 * 1024
        for item in items:
            name = item.filename.lower()
            assert not item.flag_bits & 1
            assert not any(part in name for part in ('vbaproject', 'embeddings/', 'externallinks/'))
            if name.endswith(('.xml', '.rels')):
                value = archive.read(item)
                assert b'<!DOCTYPE' not in value and b'<!ENTITY' not in value
                assert b'TargetMode="External"' not in value
                assert not re.search(rb'<(?:[a-zA-Z]+:)?f(?:\s|>)', value)


def pdf(path):
    from pypdf import PdfReader
    reader = PdfReader(path)
    assert not reader.is_encrypted and len(reader.pages) <= 100
    seen = set()
    count = 0
    def visit(value, depth=0):
        nonlocal count
        assert depth < 50 and count < 20000
        if hasattr(value, 'get_object'):
            value = value.get_object()
        if id(value) in seen:
            return
        seen.add(id(value))
        count += 1
        if isinstance(value, dict):
            assert not set(value).intersection({'/JS', '/JavaScript', '/Launch', '/EmbeddedFiles', '/OpenAction', '/AA', '/AcroForm', '/RichMedia', '/XFA', '/URI', '/SubmitForm', '/GoToR'})
            for item in value.values():
                visit(item, depth+1)
        elif isinstance(value, list):
            for item in value:
                visit(item, depth+1)
    visit(reader.trailer)


def validate(path):
    suffix = path.suffix.lower()
    if suffix in ('.xlsx', '.docx'):
        office(path)
    elif suffix == '.pdf':
        pdf(path)
    elif suffix == '.png':
        from PIL import Image
        Image.MAX_IMAGE_PIXELS = 20000000
        with Image.open(path) as image:
            assert image.format == 'PNG' and image.width * image.height <= 20000000
            image.verify()
    elif suffix == '.csv':
        csv.field_size_limit(1024 * 1024)
        with path.open(encoding='utf-8-sig', newline='') as stream:
            for index, row in enumerate(csv.reader(stream)):
                assert index < 100000
                for cell in row:
                    text = cell.lstrip()
                    if text.startswith(('=', '+', '-', '@')):
                        assert re.fullmatch(r'-?\d+(?:\.\d+)?', text)
    else:
        raise ValueError('Unsupported artifact type')


for item in Path('/inputs').iterdir():
    validate(item)
print('VALIDATED')
