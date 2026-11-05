import sys,json,re,io,datetime
from pathlib import Path

def blue(rgb):
    if not rgb:return False
    r,g,b=rgb[-3:]
    return b>=120 and b>r*1.25 and b>g*1.1

def number(v):
    if isinstance(v,(int,float)) and not isinstance(v,bool):return float(v)
    if isinstance(v,str) and re.fullmatch(r'\s*\d+(\.\d+)?\s*',v):return float(v)
    return None

def text(v):
    if v is None:return ''
    if isinstance(v,datetime.datetime):return v.date().isoformat()
    if isinstance(v,float) and v.is_integer():return str(int(v))
    return str(v).strip()

def read(path):
    result=[]
    if Path(path).suffix.lower()=='.xls':
        import xlrd
        book=xlrd.open_workbook(path,formatting_info=True)
        for sheet in book.sheets():
            rows=[];colors=[]
            for i in range(sheet.nrows):
                values=[];flags=[]
                for j in range(sheet.ncols):
                    cell=sheet.cell(i,j);value=cell.value
                    if cell.ctype==xlrd.XL_CELL_DATE:value=xlrd.xldate_as_datetime(value,book.datemode)
                    values.append(value)
                    xf=book.xf_list[cell.xf_index];font=book.font_list[xf.font_index]
                    flags.append(blue(book.colour_map.get(font.colour_index)) or blue(book.colour_map.get(xf.background.pattern_colour_index)))
                rows.append(values);colors.append(flags)
            result.append((sheet.name,rows,colors))
    else:
        import openpyxl
        from openpyxl.styles.colors import COLOR_INDEX
        book=openpyxl.load_workbook(path,data_only=True)
        def rgb(c):
            if not c:return None
            value=c.rgb if c.type=='rgb' else COLOR_INDEX[c.indexed] if c.type=='indexed' and c.indexed<len(COLOR_INDEX) else None
            if not isinstance(value,str) or len(value)<6:return None
            try:return tuple(int(value[-6:][i:i+2],16) for i in (0,2,4))
            except ValueError:return None
        for sheet in book:
            rows=[];colors=[]
            for cells in sheet:
                rows.append([c.value for c in cells]);colors.append([blue(rgb(c.font.color)) or (c.fill.patternType=='solid' and blue(rgb(c.fill.fgColor))) for c in cells])
            result.append((sheet.title,rows,colors))
    return result

def col(value,default):
    if value is None:return default
    if isinstance(value,int) and 0<=value<100:return value
    if isinstance(value,str) and re.fullmatch('[A-Z]{1,2}',value):
        n=0
        for c in value:n=n*26+ord(c)-64
        return n-1
    raise ValueError('字段映射列无效')

def parse(sheets,kind,options):
    chosen=next((s for s in sheets if s[0]==options.get('sheet')),sheets[0]);name,rows,colors=chosen
    result=[];category='其他';category_group='';headers=[];mapping=options.get('mapping',{})
    get=lambda row,j:row[j] if j<len(row) else None
    if kind=='INITIAL':
        hi=next((i for i,r in enumerate(rows) if any('资产数量' in text(v) for v in r)),None)
        if hi is None:raise ValueError('未找到资产数量表头，请确认期初总库工作表')
        title=' '.join(text(v) for r in rows[:hi] for v in r if text(v))
        category_group='机械器材' if re.search(r'机械.*总库',title) else ''
        headers=rows[hi]
        for i,r in enumerate(rows[hi+1:],hi+1):
            category=text(get(r,0)) or category;raw=text(get(r,1))
            if not raw:continue
            total=number(get(r,2))
            bj,hd=[0 if not text(get(r,j)) or re.fullmatch(r'[-—–]+',text(get(r,j))) else number(get(r,j)) for j in [3,4]];out=[];issues=[]
            for j in range(5,min(14,len(r))):
                q=number(r[j])
                if q and q>0:out.append({'name':text(headers[j]),'quantity':q,'kind':'CREW'})
            other=text(get(r,14))
            if other:
                for part in other.splitlines():
                    m=re.fullmatch(r'(.+?)[*×x]\s*(\d+(?:\.\d+)?)',part.strip())
                    if m:out.append({'name':m[1].strip(),'quantity':float(m[2]),'kind':'OTHER'})
                    else:issues.append('其他出库包含未识别内容：'+part)
            note=text(get(r,15));owner=2 if any(colors[i][:2]) else 1
            if total is None or bj is None or hd is None:issues.append('资产总数未填写或分仓数量无效，需补充核对（分仓空白按0）')
            distribution=(bj or 0)+(hd or 0)+sum(x['quantity'] for x in out)
            if total is not None and abs(total-distribution)>0.000001:issues.append(f'资产{total:g}，已分配{distribution:g}，差额{total-distribution:g}')
            result.append({'rawName':raw,'quantity':total if total is not None else distribution,'ownerId':owner,'partNo':'2' if owner==2 else '', 'serials':[],'category':category,'categoryGroup':category_group,'unit':'件','note':note,'otherOriginal':other,'sourceRow':i+1,'sheet':name,'initial':{'total':total,'beijing':bj,'hengdian':hd,'outbounds':out},'parserIssues':issues})
    else:
        ni=col(mapping.get('name'),1);qi=col(mapping.get('quantity'),2);pi=col(mapping.get('partNo'),0);si=col(mapping.get('serials'),4);ui=col(mapping.get('unit'),3);ri=col(mapping.get('note'),5)
        hi=next((i for i,r in enumerate(rows) if '器材名称' in text(get(r,ni)) or '设备名称' in text(get(r,ni))),None)
        start=int(options.get('startRow') or ((hi+2) if hi is not None else 1))-1
        end=int(options.get('endRow') or len(rows))
        for i,r in enumerate(rows[start:end],start):
            raw=text(get(r,ni));q=number(get(r,qi))
            if not raw:continue
            if q is None:
                if not any(text(get(r,j)) for j in [pi,si,ui,ri]):category=raw
                continue
            serial=text(get(r,si));serials=[x for x in re.split(r'[\s,，;；]+',serial) if x]
            result.append({'rawName':raw,'quantity':q,'partNo':text(get(r,pi)),'serials':serials,'unit':text(get(r,ui)) or '件','note':text(get(r,ri)),'category':category,'sourceRow':i+1,'sheet':name,'parserIssues':[]})
    context={}
    if kind=='INITIAL':context['categoryGroup']=category_group
    for i,r in enumerate(rows[:12]):
        for j,v in enumerate(r):
            val=text(v)
            if '项目备注' in val and j+1<len(r):context['projectName']=text(r[j+1])
            if '归还日期' in val and j+1<len(r):context['returnDate']=text(r[j+1])
            if '出库日期' in val and j+1<len(r):context['startDate']=text(r[j+1])
    return {'sheet':name,'rows':result,'context':context,'totalRows':len(rows),'headers':[text(x) for x in headers]}

def main():
    mode=sys.argv[1]
    if mode=='export':
        import openpyxl
        from openpyxl.styles import Font,PatternFill,Alignment
        from openpyxl.utils import get_column_letter
        data=json.load(sys.stdin);b=openpyxl.Workbook();s=b.active;s.title=data.get('title','设备清单')[:31]
        for row in data['rows']:
            s.append(row)
            for cell,value in zip(s[s.max_row],row):
                if isinstance(value,str):cell.data_type='s'
        for cell in s[1]:cell.font=Font(bold=True,color='FFFFFF');cell.fill=PatternFill('solid',fgColor='24569C')
        for row in s:
            for cell in row:cell.alignment=Alignment(vertical='center',wrap_text=True)
        for col_i in range(1,s.max_column+1):s.column_dimensions[get_column_letter(col_i)].width=24
        s.freeze_panes='A2';buffer=io.BytesIO();b.save(buffer);sys.stdout.buffer.write(buffer.getvalue());return
    sheets=read(sys.argv[2]);options=json.load(sys.stdin) if mode=='parse' else {}
    output={'sheets':[{'name':n,'rows':len(r),'columns':max((len(v) for v in r),default=0)} for n,r,c in sheets]}
    if mode=='parse':output.update(parse(sheets,sys.argv[3],options))
    print(json.dumps(output,ensure_ascii=False,default=text))

if __name__=='__main__':
    try:main()
    except Exception as error:
        print(json.dumps({'error':str(error)},ensure_ascii=False));sys.exit(1)

