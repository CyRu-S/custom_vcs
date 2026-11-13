"""Build the learning guide from project-owned Markdown, with vector diagrams and page bookmarks."""
from pathlib import Path
from html import escape
import re
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    Preformatted, Image, KeepTogether, Flowable,
)
from reportlab.platypus.tableofcontents import TableOfContents

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pdf/CustomVCS-Project-Guide.pdf'
OUT.parent.mkdir(parents=True, exist_ok=True)
FONT_DIR = Path('C:/Windows/Fonts')
pdfmetrics.registerFont(TTFont('Body', str(FONT_DIR / 'segoeui.ttf')))
pdfmetrics.registerFont(TTFont('BodyBold', str(FONT_DIR / 'segoeuib.ttf')))
pdfmetrics.registerFont(TTFont('Code', str(FONT_DIR / 'consola.ttf')))
pdfmetrics.registerFontFamily('Body', normal='Body', bold='BodyBold', italic='Body', boldItalic='BodyBold')
BLUE = colors.HexColor('#315bfa')
INK = colors.HexColor('#202b44')
MUTED = colors.HexColor('#5e6b83')
LINE = colors.HexColor('#dfe5f1')
SOFT = colors.HexColor('#eef2ff')
WIDTH = A4[0] - 100
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='TextBody', fontName='Body', fontSize=10.2, leading=15.6,
                          textColor=INK, spaceAfter=10))
styles.add(ParagraphStyle(name='Chapter', fontName='BodyBold', fontSize=24, leading=30,
                          textColor=INK, spaceBefore=12, spaceAfter=20, keepWithNext=True))
styles.add(ParagraphStyle(name='Subhead', fontName='BodyBold', fontSize=13, leading=18,
                          textColor=BLUE, spaceBefore=13, spaceAfter=9, keepWithNext=True))
styles.add(ParagraphStyle(name='IntroTitle', parent=styles['Chapter']))
styles.add(ParagraphStyle(name='BulletBody', parent=styles['TextBody'], leftIndent=13,
                          firstLineIndent=-10, spaceAfter=7))
styles.add(ParagraphStyle(name='SmallBody', parent=styles['TextBody'], fontSize=8.2, leading=11.5,
                          spaceAfter=3, wordWrap='CJK'))
styles.add(ParagraphStyle(name='Caption', parent=styles['TextBody'], fontSize=8.5, leading=12,
                          textColor=MUTED, spaceBefore=8, spaceAfter=15))
styles.add(ParagraphStyle(name='MonoBlock', fontName='Code', fontSize=8, leading=11,
                          textColor=INK, backColor=colors.HexColor('#f6f7fb'), borderPadding=10,
                          spaceBefore=7, spaceAfter=15))

def inline(text):
    value = escape(text)
    value = re.sub(r'`([^`]+)`', r'<font name="Code">\1</font>', value)
    # Long URLs wrap in small body text; external links remain clickable.
    value = re.sub(r'(https?://[^\s]+)', r'<link href="\1" color="#315bfa">\1</link>', value)
    return value

class Diagram(Flowable):
    def __init__(self, kind):
        Flowable.__init__(self)
        self.kind, self.width = kind, WIDTH
        self.height = {'architecture': 225, 'states': 128, 'auth': 160, 'merge': 158}[kind]

    def draw(self):
        c = self.canv
        def box(x,y,w,h,title,sub=''):
            c.setFillColor(SOFT); c.setStrokeColor(LINE)
            c.roundRect(x,y,w,h,8,fill=1,stroke=1)
            c.setFillColor(INK); c.setFont('BodyBold',10)
            c.drawCentredString(x+w/2,y+h-21,title)
            if sub:
                c.setFillColor(MUTED); c.setFont('Body',8)
                c.drawCentredString(x+w/2,y+13,sub)
        def arrow(x,y,xx,yy):
            c.setStrokeColor(BLUE); c.setFillColor(BLUE); c.setLineWidth(1.4)
            c.line(x,y,xx,yy)
            import math
            a=math.atan2(yy-y,xx-x)
            p=c.beginPath(); p.moveTo(xx,yy)
            p.lineTo(xx-6*math.cos(a-.45),yy-6*math.sin(a-.45))
            p.lineTo(xx-6*math.cos(a+.45),yy-6*math.sin(a+.45)); p.close()
            c.drawPath(p,fill=1,stroke=0)
        if self.kind=='architecture':
            box(0,160,145,58,'React workspace','Browser drafts + real API')
            box(195,160,300,58,'Spring Boot + Security','Session / CSRF / ownership / controllers')
            arrow(145,189,195,189)
            box(195,72,150,58,'vcs-core','Objects / index / commits')
            box(0,72,145,58,'Picocli','Local commands; no server')
            arrow(145,101,195,101); arrow(270,160,270,130)
            box(370,72,125,58,'PostgreSQL','Users + metadata')
            arrow(425,160,425,130)
            box(195,0,300,48,'Filesystem repository storage','Working files + .customvcs')
            arrow(270,72,270,48)
        elif self.kind=='states':
            box(0,58,140,60,'Working tree','Editable bytes')
            box(177,58,140,60,'Index','Next snapshot')
            box(354,58,140,60,'HEAD snapshot','Last committed state')
            arrow(140,88,177,88); arrow(317,88,354,88)
            c.setFillColor(MUTED); c.setFont('Body',8)
            c.drawCentredString(158,45,'add'); c.drawCentredString(335,45,'commit')
            c.drawCentredString(247,18,'status = HEAD vs index + index vs working tree')
        elif self.kind=='auth':
            steps=[('GET CSRF','Session cookie + token'),('POST login','Cookie + CSRF header'),('API request','Identity + owner check'),('Save context','Rotate session ID')]
            for i,(title,sub) in enumerate(steps):
                x=(i%2)*260; y=88 if i<2 else 8
                box(x,y,235,62,title,sub)
            arrow(235,119,260,119); arrow(377,88,377,70); arrow(260,39,235,39)
        else:
            pts={'A':(65,78),'B':(200,125),'C':(200,28),'M':(395,78)}
            for a,b in [('A','B'),('A','C'),('B','M'),('C','M')]: arrow(*pts[a],*pts[b])
            for name,(x,y) in pts.items():
                c.setFillColor(BLUE); c.circle(x,y,14,fill=1,stroke=0)
                c.setFillColor(colors.white); c.setFont('BodyBold',11); c.drawCentredString(x,y-4,name)
            c.setFillColor(MUTED); c.setFont('Body',8)
            for x,y,label in [(65,52,'base'),(200,145,'ours'),(200,3,'theirs'),(395,52,'two-parent merge')]:
                c.drawCentredString(x,y,label)

class GuideDoc(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name=='Chapter':
            text=flowable.getPlainText(); key='chapter-'+text.split(' / ')[0]
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(text,key,0,False)
            self.notify('TOCEntry',(0,text,self.page,key))

def chrome(canvas, doc):
    canvas.saveState()
    if doc.page>1:
        canvas.setStrokeColor(LINE); canvas.line(50,A4[1]-39,A4[0]-50,A4[1]-39)
        canvas.setFont('BodyBold',8); canvas.setFillColor(BLUE)
        canvas.drawString(50,A4[1]-28,'CUSTOMVCS / PROJECT WORKFLOW')
        canvas.setFont('Body',8); canvas.setFillColor(MUTED)
        canvas.drawString(50,29,'Java engine  /  Spring Boot  /  React')
        canvas.drawRightString(A4[0]-50,29,str(doc.page))
    canvas.restoreState()

story=[]
story += [Spacer(1,70),Paragraph('CUSTOMVCS',ParagraphStyle('CoverTag',fontName='BodyBold',fontSize=12,textColor=BLUE,spaceAfter=22)),
          Paragraph('Your project.<br/>From bytes<br/>to browser.',ParagraphStyle('CoverTitle',fontName='BodyBold',fontSize=43,leading=49,textColor=INK)),
          Spacer(1,25),Paragraph('A detailed workflow and learning guide',styles['Subhead']),
          Paragraph('Understand the engine, authenticated backend, connected React workspace, tests and deployment - one state transition at a time.',styles['TextBody']),
          Spacer(1,28),Diagram('states'),Spacer(1,34),
          Paragraph('Edition 1.0 / 03 November 2026<br/>Based on the implemented CustomVCS source',styles['Caption']),PageBreak()]
story.append(Paragraph('Read this as a map',styles['IntroTitle']))
story.append(Paragraph('Start with architecture and the three file states. Trace a file through staging and a commit, then move to security, checkout and merging. Chapters are short enough to revisit alongside the source.',styles['TextBody']))
toc=TableOfContents()
toc.levelStyles=[ParagraphStyle('TOC',fontName='Body',fontSize=9,leading=12.5,textColor=INK,leftIndent=0,firstLineIndent=0,spaceBefore=0)]
story.append(toc)

lines=(ROOT/'docs/PROJECT-WORKFLOW.md').read_text(encoding='utf-8').splitlines()
i=0
while i<len(lines):
    line=lines[i].strip()
    if not line: i+=1; continue
    if line.startswith('# '):
        story.extend([PageBreak(),Paragraph(inline(line[2:]),styles['Chapter'])]); i+=1; continue
    if line.startswith('## '):
        story.append(Paragraph(inline(line[3:]),styles['Subhead'])); i+=1; continue
    if line.startswith('[diagram:'):
        story.extend([Diagram(line[9:-1]),Spacer(1,15)]); i+=1; continue
    if line.startswith('[screenshot:'):
        kind=line[12:-1]
        path=ROOT/f'docs/images/connected-{kind}.png'
        if path.exists():
            if kind in ('merge', 'mobile'):
                story.extend([PageBreak(), Paragraph('Conflict resolution / browser view' if kind=='merge' else 'An empty repository / narrow screen', styles['Subhead'])])
            from PIL import Image as PILImage
            with PILImage.open(path) as im: w,h=im.size
            max_height=580 if kind=='merge' else 490 if kind=='mobile' else 420
            scale=min(WIDTH/w,max_height/h)
            story.append(Image(str(path),width=w*scale,height=h*scale,hAlign='CENTER'))
            story.append(Paragraph('Verified browser workflow / '+kind,styles['Caption']))
        i+=1; continue
    if line.startswith('```'):
        code=[]; i+=1
        while i<len(lines) and not lines[i].strip().startswith('```'):
            value=lines[i]
            # Fixed-width code must not run into the page margin.
            while len(value)>90:
                code.append(value[:89]+'\\'); value='  '+value[89:]
            code.append(value); i+=1
        story.append(Preformatted('\n'.join(code),styles['MonoBlock'])); i+=1; continue
    if line.startswith('|'):
        rows=[]
        while i<len(lines) and lines[i].strip().startswith('|'):
            cells=[c.strip() for c in lines[i].strip().strip('|').split('|')]
            if not all(re.fullmatch('[-: ]+',c) for c in cells): rows.append(cells)
            i+=1
        columns=len(rows[0]); widths=[WIDTH/columns]*columns
        if columns==3: widths=[WIDTH*.27,WIDTH*.34,WIDTH*.39]
        if columns==2: widths=[WIDTH*.34,WIDTH*.66]
        data=[[Paragraph(('<b>'+inline(c)+'</b>') if j==0 else inline(c),styles['SmallBody']) for c in row] for j,row in enumerate(rows)]
        t=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT')
        t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),SOFT),('VALIGN',(0,0),(-1,-1),'TOP'),
                              ('LINEBELOW',(0,0),(-1,0),.7,LINE),('LINEBELOW',(0,1),(-1,-1),.4,LINE),
                              ('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6),
                              ('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8)]))
        story.extend([t,Spacer(1,12)]); continue
    if line.startswith('- ') or re.match(r'^\d+\. ',line):
        value='- '+line[2:] if line.startswith('- ') else line
        story.append(Paragraph(inline(value),styles['BulletBody'])); i+=1; continue
    paragraph=[line]; i+=1
    while i<len(lines) and lines[i].strip() and not re.match(r'^(#|\||```|\[diagram:|\[screenshot:|- |\d+\. )',lines[i].strip()):
        paragraph.append(lines[i].strip()); i+=1
    value=' '.join(paragraph)
    story.append(Paragraph(inline(value),styles['SmallBody'] if 'https://' in value else styles['TextBody']))

doc=GuideDoc(str(OUT),pagesize=A4,rightMargin=50,leftMargin=50,topMargin=58,bottomMargin=52,
             title='CustomVCS - Project Workflow and Learning Guide',author='CustomVCS',
             subject='Engine, backend, authentication, frontend, testing and deployment')
doc.multiBuild(story,onFirstPage=chrome,onLaterPages=chrome)
print(OUT)
