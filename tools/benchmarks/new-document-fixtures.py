"""Generate original KAN-31 instrumentation fixtures."""

from io import BytesIO
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pdfencrypt import StandardEncryption
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2] / "app/src/documentSdkTest/assets/documents"
ROOT.mkdir(parents=True, exist_ok=True)


def new_pdf(name, title, *, encryption=None):
    pdf = canvas.Canvas(
        str(ROOT / name), pagesize=(432, 576), invariant=True, encrypt=encryption
    )
    pdf.setTitle(title)
    return pdf


def heading(pdf, title, body):
    pdf.setFont("Helvetica-Bold", 24)
    pdf.drawString(36, 520, title)
    pdf.setFont("Helvetica", 13)
    pdf.drawString(36, 477, body)


pdf = new_pdf("reader.pdf", "Reader fixture")
heading(pdf, "Workspace documents", "First-page search marker: cedar.")
pdf.linkURL("https://example.invalid/", (36, 473, 210, 490))
pdf.showPage()
heading(pdf, "Study notes", "Second-page search marker: juniper.")
pdf.acroForm.textfield(
    name="reader_note", value="Original", x=36, y=398, width=240, height=28
)
pdf.showPage()
pdf.save()

pdf = new_pdf("scanned.pdf", "Image-only fixture")
scan = Image.new("RGB", (720, 420), "white")
draw = ImageDraw.Draw(scan)
font = ImageFont.load_default(size=32)
draw.text((60, 72), "Scanned study note", fill="#242044", font=font)
draw.text((60, 145), "Raster-only marker: fern", fill="#242044", font=font)
buffer = BytesIO()
scan.save(buffer, format="PNG")
buffer.seek(0)
pdf.drawImage(ImageReader(buffer), 36, 275, 360, 210)
pdf.showPage()
pdf.save()

pdf = new_pdf("many-pages.pdf", "Page-count fixture")
for number in range(1, 129):
    heading(pdf, f"Reference page {number}", "Synthetic text page for a rendering pass.")
    pdf.showPage()
pdf.save()

encryption = StandardEncryption("fixture-reader", ownerPassword="fixture-owner", strength=128)
pdf = new_pdf("protected.pdf", "Password fixture", encryption=encryption)
heading(pdf, "Protected study note", "Authorized search marker: kauri.")
pdf.showPage()
pdf.save()


def chapter(title, text, *, reflow=False):
    before = "<p>Notes on local document reading and paragraph layout.</p>" * 35 if reflow else ""
    after = "<p>End-of-chapter notes for the reading fixture.</p>" * 20 if reflow else ""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
<head><title>{title}</title><meta name="viewport" content="width=device-width, initial-scale=1"/></head>
<body><h1>{title}</h1>{before}<p id="reading-anchor">{text}</p>{after}</body>
</html>"""


def epub_entry(archive, name, content, compression):
    entry = ZipInfo(name, date_time=(2026, 11, 24, 0, 0, 0))
    archive.writestr(entry, content, compress_type=compression)


with ZipFile(ROOT / "reader.epub", "w") as epub:
    epub_entry(epub, "mimetype", "application/epub+zip", ZIP_STORED)
    resources = {
        "META-INF/container.xml": """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
<rootfiles><rootfile full-path="EPUB/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""",
        "EPUB/book.opf": """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="fixture" xml:lang="en">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="fixture">urn:omninumeris:document-fixture</dc:identifier>
<dc:title>Offline Reading Fixture</dc:title><dc:language>en</dc:language>
<meta property="dcterms:modified">2026-11-24T00:00:00Z</meta>
</metadata>
<manifest>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="first" href="first.xhtml" media-type="application/xhtml+xml"/>
<item id="second" href="second.xhtml" media-type="application/xhtml+xml"/>
</manifest><spine><itemref idref="first"/><itemref idref="second"/></spine>
</package>""",
        "EPUB/nav.xhtml": """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head><body><nav epub:type="toc"><ol>
<li><a href="first.xhtml">First chapter</a></li>
<li><a href="second.xhtml">Second chapter</a></li>
</ol></nav></body></html>""",
        "EPUB/first.xhtml": chapter("First chapter", "A short chapter for offline reading."),
        "EPUB/second.xhtml": chapter("Second chapter", "A stable sentence marks the reading location.", reflow=True),
    }
    for name, content in resources.items():
        epub_entry(epub, name, content, ZIP_DEFLATED)
