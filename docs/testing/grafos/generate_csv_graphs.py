"""Grafos del ticket P2-16. Requiere Graphviz instalado."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
GRAPHS = [
    ('04-reporte-csv', 'EventReportService.generateEventCsv - modelo de flujo', {
        1: 'Inicio', 2: 'Obtener analytics, event y metrics',
        3: 'getImpactScore retorna normalmente?', 4: 'Preparar 16 filas y cabecera con BOM',
        5: 'Quedan filas por serializar?', 6: 'Escapar las tres celdas de la fila',
        7: 'Unir con comas y agregar CRLF', 8: 'Retornar bytes UTF-8',
        9: 'Propagar excepción del puntaje', 10: 'Salida unificada',
    }, [(1,2,''),(2,3,''),(3,4,'V'),(3,9,'F'),(4,5,''),(5,6,'V'),
        (6,7,''),(7,5,''),(5,8,'F'),(8,10,''),(9,10,'')], [3,5]),
    ('05-escape-csv', 'EventReportService.escapeCsv - cortocircuito', {
        1: 'Inicio: safeValue = value', 2: 'Reconocer número negativo (regex)',
        3: 'safeValue no vacío?', 4: 'Primer carácter pertenece a =+@?',
        5: 'safeValue empieza con menos?', 6: 'No es un número negativo?',
        7: 'Anteponer apóstrofo', 8: 'Duplicar comillas y envolver el campo',
        9: 'Retornar campo escapado',
    }, [(1,2,''),(2,3,''),(3,4,'V'),(3,5,'F'),(4,7,'V'),(4,5,'F'),
        (5,6,'V'),(5,8,'F'),(6,7,'V'),(6,8,'F'),(7,8,''),(8,9,'')], [3,4,5,6]),
]

for name,title,nodes,edges,predicates in GRAPHS:
    complexity = len(edges)-len(nodes)+2
    assert complexity == len(predicates)+1
    q=lambda s:json.dumps(s,ensure_ascii=False)
    lines=['digraph CFG {',
        'graph [bgcolor="white", ranksep=0.38, nodesep=0.5, pad=0.25, labelloc=t, fontname="Arial", fontsize=17,',
        'label='+q(title+'\nN = '+str(len(nodes))+' | E = '+str(len(edges))+' | V(G) = '+str(complexity))+'];',
        'node [shape=circle, width=0.45, fixedsize=true, fontname="Arial", style=filled, fillcolor=white, color="#334155"];',
        'edge [fontname="Arial", fontsize=11, color="#475569"];']
    for n in nodes:
        lines.append(f'{n} [label="{n}",fillcolor="'+('#dbeafe' if n in predicates else 'white')+'"];')
    for a,b,label in edges:lines.append(f'{a} -> {b} [label={q(label)}];')
    legend='\\l'.join(str(n)+'. '+s for n,s in nodes.items())+'\\l'
    lines.extend(['legend [shape=box,fixedsize=false,style=rounded,color="#cbd5e1",fontsize=11,margin=0.15,label="'+legend+'"];',
        '{rank=same;1;legend;}','1 -> legend [style=invis];','}'])
    dot=ROOT/(name+'.dot');dot.write_text('\n'.join(lines)+'\n')
    for fmt in ['png','svg']:
        subprocess.run(['dot','-T'+fmt,'-Gdpi=140',str(dot),'-o',str(ROOT/(name+'.'+fmt))],check=True)
    print(name, 'N',len(nodes),'E',len(edges),'V(G)',complexity)
