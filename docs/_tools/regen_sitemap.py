import os, datetime
docs_dir = 'docs'
base = 'https://sauravbhattacharya001.github.io/GraphVisual/'
htmls = sorted(f for f in os.listdir(docs_dir) if f.endswith('.html'))
today = datetime.date.today().isoformat()

def prio(name):
    if name == 'index.html':
        return ('1.0', 'weekly')
    if name in ('guide.html', 'api.html', 'architecture.html', 'cookbook.html'):
        return ('0.9', 'weekly')
    return ('0.7', 'monthly')

out = ['<?xml version="1.0" encoding="UTF-8"?>',
       '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
for f in htmls:
    p, cf = prio(f)
    loc = base + (f if f != 'index.html' else '')
    out += ['  <url>',
            f'    <loc>{loc}</loc>',
            f'    <lastmod>{today}</lastmod>',
            f'    <changefreq>{cf}</changefreq>',
            f'    <priority>{p}</priority>',
            '  </url>']
out.append('</urlset>')
open(os.path.join(docs_dir, 'sitemap.xml'), 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print(f'Wrote {len(htmls)} URLs')
