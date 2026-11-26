#!/bin/bash
# Chạy toàn bộ kịch bản E2E thật (cần backend QA 18091 + Vite 5178). Mỗi kịch bản tự dựng dữ liệu riêng trên DB QA.
cd "$(dirname "$0")"
fail=0
for f in "$@"; do
  out=$(node "$f" 2>&1); rc=$?
  if [ $rc -eq 0 ]; then echo "PASS $f"; else echo "FAIL $f"; echo "$out" | tail -8 | cut -c1-200; fail=1; fi
done
exit $fail
