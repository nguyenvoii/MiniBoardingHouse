"""Hòa kết quả E2E vào docs/NGHIEM_THU_123_USE_CASE.xlsx theo mã UC (chạy khi đã đóng Excel).

Nguồn: docs/KET_QUA_E2E_NGHIEM_THU.xlsx (sheet 'UC1–UC123', cột F = kết quả E2E, cột G = bằng chứng).
Chỉ thêm/ghi đè cột F, G của file chính, giữ nguyên mọi chỉnh sửa khác của người dùng.
Dùng:  python docs/merge_nghiem_thu_e2e.py
"""
import copy
import openpyxl
from openpyxl.styles import Alignment

SRC = 'docs/KET_QUA_E2E_NGHIEM_THU.xlsx'
DST = 'docs/NGHIEM_THU_123_USE_CASE.xlsx'
SHEET = 'UC1–UC123'

src = openpyxl.load_workbook(SRC)[SHEET]
wb = openpyxl.load_workbook(DST)
dst = wb[SHEET]
for col, title in (('F', 'E2E (UI→API→SQL QA)'), ('G', 'Bằng chứng E2E 26/11/2026')):
    dst[f'{col}1'].value = title
    dst[f'{col}1']._style = copy.copy(dst['E1']._style)
dst.column_dimensions['F'].width = 18
dst.column_dimensions['G'].width = 90
by_code = {r[0].value: r for r in src.iter_rows(min_row=2)}
n = 0
for row in dst.iter_rows(min_row=2):
    code = row[0].value
    if code in by_code:
        row[5].value = by_code[code][5].value
        row[6].value = by_code[code][6].value
        row[6].alignment = Alignment(wrap_text=True, vertical='top')
        n += 1
wb.save(DST)
print(f'Đã hòa {n} dòng UC vào {DST}')
