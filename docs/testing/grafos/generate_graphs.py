"""Genera grafos de caja blanca con Graphviz: python3 generate_graphs.py."""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parent

GRAPHS = [
    {
        'file': '01-asistencia',
        'title': 'AttendeeGuard.requireCheckedIn',
        'nodes': {
            1: 'Inicio', 2: 'eventId == null?', 3: 'participantId == null?',
            4: 'No existe CHECKED_IN?', 5: 'Lanzar BusinessRuleException',
            6: 'Retornar normalmente', 7: 'Salida unificada',
        },
        'decisions': [2, 3, 4],
        'edges': [(1,2,''),(2,5,'V'),(2,3,'F'),(3,5,'V'),(3,4,'F'),
                  (4,5,'V'),(4,6,'F'),(5,7,''),(6,7,'')],
    },
    {
        'file': '02-encuestas',
        'title': 'SurveyService.submit - flujo de negocio',
        'nodes': {
            1: 'Inicio', 2: 'Evento presente?', 3: 'Lanzar ResourceNotFoundException (evento)',
            4: 'Participante presente?', 5: 'Lanzar ResourceNotFoundException (participante)',
            6: 'Encuesta ya existe?', 7: 'Lanzar DuplicateSurveyException',
            8: 'requireCheckedIn retorna normalmente?', 9: 'Propagar error de asistencia',
            10: 'Crear encuesta y asignar valores', 11: 'Comentario == null?',
            12: 'Asignar comentario null', 13: 'Asignar comentario.trim()',
            14: 'saveAndFlush retorna normalmente?', 15: 'Retornar encuesta persistida',
            16: 'isDuplicateSurveyConstraint(error)?', 17: 'Propagar error de integridad',
            18: 'Salida unificada',
        },
        'decisions': [2,4,6,8,11,14,16],
        'edges': [(1,2,''),(2,4,'V'),(2,3,'F'),(3,18,''),(4,6,'V'),(4,5,'F'),
                  (5,18,''),(6,7,'V'),(6,8,'F'),(7,18,''),(8,10,'V'),(8,9,'F'),
                  (9,18,''),(10,11,''),(11,12,'V'),(11,13,'F'),(12,14,''),
                  (13,14,''),(14,15,'V'),(14,16,'F: integridad'),(15,18,''),
                  (16,7,'V'),(16,17,'F'),(17,18,'')],
    },
    {
        'file': '03-restriccion-duplicada',
        'title': 'SurveyService.isDuplicateSurveyConstraint',
        'nodes': {
            1: 'cause = exception', 2: 'cause != null?',
            3: 'cause instanceof ConstraintViolationException?',
            4: 'constraintName == uk_surveys_event_participant?',
            5: 'Retornar true', 6: 'cause = cause.getCause()',
            7: 'Retornar false', 8: 'Salida unificada',
        },
        'decisions': [2,3,4],
        'edges': [(1,2,''),(2,3,'V'),(2,7,'F'),(3,4,'V'),(3,6,'F'),
                  (4,5,'V'),(4,6,'F'),(6,2,''),(5,8,''),(7,8,'')],
    },
]

for graph in GRAPHS:
    nodes, edges = graph['nodes'], graph['edges']
    complexity = len(edges) - len(nodes) + 2
    assert complexity == len(graph['decisions']) + 1
    assert all(a in nodes and b in nodes for a,b,_ in edges)
    quote = lambda value: json.dumps(str(value), ensure_ascii=False)
    legend = '\\l'.join(f'{n}. {label}' for n,label in nodes.items()) + '\\l'
    lines = [
        'digraph CFG {',
        'graph [rankdir=TB, bgcolor="white", pad="0.3", nodesep="0.55", ranksep="0.48",',
        f'label={quote(graph["title"] + chr(10) + f"N = {len(nodes)} | E = {len(edges)} | V(G) = {complexity}")},',
        'labelloc=t, fontname="Arial", fontsize=19];',
        'node [shape=circle, width=0.48, fixedsize=true, style=filled, fillcolor="#ffffff", color="#334155", fontname="Arial", fontsize=13];',
        'edge [fontname="Arial", fontsize=11, color="#475569", arrowsize=0.7];',
    ]
    for n in nodes:
        color = '#dbeafe' if n in graph['decisions'] else '#ffffff'
        lines.append(f'{n} [label="{n}", fillcolor="{color}"];')
    for a,b,label in edges:
        lines.append(f'{a} -> {b} [label={quote(label)}];')
    lines += [f'legend [shape=box, fixedsize=false, style="rounded", fillcolor="white", color="#cbd5e1", margin="0.2", label="{legend}", fontsize=12];',
              '{rank=same; 1; legend;}', '1 -> legend [style=invis];', '}']
    dot = ROOT / (graph['file'] + '.dot')
    dot.write_text('\n'.join(lines) + '\n')
    for fmt in ['svg','png']:
        subprocess.run(['dot', f'-T{fmt}', '-Gdpi=150', str(dot), '-o', str(ROOT/(graph['file']+'.'+fmt))], check=True)
    print(graph['file'], 'N=',len(nodes),'E=',len(edges),'V(G)=',complexity)
