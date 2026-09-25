"""
The same dump as org.opendiplom.tools.RegressionDump, made by the Python
service this project replaces (diploma_supplement_service).

Usage:
    python python_dump.py <service repo> statement|plan|xml <list file>

Each line of the list file holds tab-separated paths. Run it with the virtual
environment of python-engine (statement, plan) or python-xml-engine (xml).
The output holds personal data: keep it on the machine that has the files.
"""

import json
import logging
import math
import sys
import warnings
from datetime import date

warnings.filterwarnings('ignore')
logging.disable(logging.CRITICAL)


def text(value) -> str:
    """xml_generator.text_value: the value as the operator reads it."""
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return ''
    if isinstance(value, str) and value.strip().lower() in ('', 'nan', 'nat', 'none'):
        return ''
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return ' '.join(str(value).split())


def number(value):
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    return float(value)


def statement(repo, files):
    sys.path.insert(0, f'{repo}/python-engine')
    from app import parser
    from app.excel import WorkbookError

    parser.parse_discipline = lambda scores, _: scores
    scores = open(files[0], 'rb').read()
    plan = open(files[1], 'rb').read() if len(files) > 1 else None
    try:
        raw, _, check = parser.process_student_workbook(scores, b'', plan)
    except WorkbookError as error:
        return {'error': str(error)}
    students = list(raw.columns)
    return {
        'students': students,
        'labels': list(raw.index),
        'grades': {
            label: [text(raw.loc[label, s]) if not hasattr(raw.loc[label, s], 'shape') else '?'
                    for s in students]
            for label in raw.index
        },
        'checks': [
            [row['Строка сводной'], int(row['з.е. по часам ведомости']),
             number(row['з.е. по учебному плану']), row['Источник з.е.'], row['Проверить']]
            for _, row in check.iterrows()
        ],
    }


def plan(repo, files):
    sys.path.insert(0, f'{repo}/python-engine')
    from app.curriculum import plan_credits
    from app.excel import WorkbookError

    try:
        credits, sheet = plan_credits(open(files[0], 'rb').read())
    except WorkbookError as error:
        return {'error': str(error)}
    return {'sheet': sheet, 'credits': {k: float(v) for k, v in credits.items()}}


def xml(repo, files):
    """The /generate-xml flow of python-xml-engine, without HTTP."""
    sys.path.insert(0, f'{repo}/python-xml-engine')
    from app.excel import WorkbookError, open_workbook
    from app.xml_generator import DataValidationError, DiplomaXMLGenerator

    try:
        pivot = open_workbook(open(files[0], 'rb').read(), 'Сводная таблица').parse(0, header=0)
        pivot.dropna(inplace=True, axis=0, how='all')
        if 'Дисциплины' not in pivot.columns:
            return {'error': 'Сводная таблица: на первом листе нет колонки «Дисциплины». '
                             'Загрузите сводную, построенную на вкладке «Сводная таблица».'}
        students = open_workbook(open(files[1], 'rb').read(), 'Сведения о студентах').parse(0)
        generator = DiplomaXMLGenerator({
            'edu_term': '4 года', 'qualification': 'бакалавр', 'edu_form': 'очная',
            'direction': '11.03.02 ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ',
            'profile': 'Интеллектуальные телекоммуникационные системы и сети',
            'edu_progr_vol': 240, 'edu_progr_vol_contact': '3180 ак.час',
            'pract_total_z_e': 12, 'gia_z_e': 9, 'gek_chairman': 'Председатель ГЭК',
            'state_exam_credits': 6,
        })
        pivot.set_index('Дисциплины', inplace=True)
        return {'xml': generator.generate_xml(pivot, students)}
    except (WorkbookError, DataValidationError) as error:
        return {'error': str(error)}


def main():
    repo, mode, listing = sys.argv[1:4]
    run = {'statement': statement, 'plan': plan, 'xml': xml}[mode]
    for line in open(listing, encoding='utf-8'):
        files = line.rstrip('\n').split('\t')
        if not files[0]:
            continue
        try:
            result = run(repo, files)
        except Exception as error:  # a crash is a result too: compare the kind
            result = {'crash': type(error).__name__}
        print(json.dumps({'files': files, **result}, ensure_ascii=False))


if __name__ == '__main__':
    main()
