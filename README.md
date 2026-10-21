# Term_Os_01
วิธีรัน เปิดไฟล์ os-term-project-starter

#แล้วเปิด terminal ใน Visual Studio Code

```bash
cd src
javac *.java
java Main ../workloads/jobs_standard.csv priority 3 1 2 > ../logs/Jobs_log.log
```

ส่วนของ jobs_standard.csv priority 3 1 2 สามารถเปลี่ยนได้โดยจะเป็น

[ชื่อไฟล์ jobs ใน workloads แบบ.csv] [รูปแบบ โดยจะมี FCFS หรือ priority] [จำนวน worker] [จำนวน printer] [จำนวน resourse]
