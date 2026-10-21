# วิธีรัน OS_Term_Project_01
เปิดไฟล์ os-term-project-01 ใน Visual Studio Code

แล้วเปิด terminal ใน Visual Studio Code

```bash
cd src
javac *.java
java Main ../workloads/jobs_standard.csv priority 3 1 2 > ../logs/Jobs_log.log
```

ส่วนของ jobs_standard.csv priority 3 1 2 สามารถเปลี่ยนได้โดยจะเป็น

[ชื่อไฟล์ jobs ใน workloads แบบ.csv] [รูปแบบ โดยจะมี FCFS หรือ priority] [จำนวน worker] [จำนวน printer] [จำนวน resourse]

# วิธีรัน OS_Term_Project_02
เปิดไฟล์ OS_Term_Project_02 ใน Visual Studio Code

## เริ่มเปิด Server
เปิด terminal ใน Visual Studio Code ไว้สำหรับเปิดรัน Server แล้วพิมพ์คำสั่งใน terminal

```bash
javac -d src src\*.java
```

ถ้าต้องการ Server แบบ Traditional I/O พิมพ์
```bash
java -cp src ServerTraditional
```

แล้วถ้าต้องการ Server แบบNIO/native transfer พิมพ์
```bash
java -cp src ServerNIO
```

หมายเหตุ: ให้เปิด Server สำหรับรัน แค่ตัวใดตัวหนึ่ง เท่านั้น

## เริ่มเปิด Client
Spilt Terminal ใน Visual Studio Code ไว้สำหรับเปิดรัน Client แล้วพิมพ์คำสั่งใน terminal
```bash
java -cp src Client
```

แล้วหลังจากนั้นก็สามารถเลือกพิมพ์ต่อตามตัวเลือกที่แสดงขึ้นมาได้เลย
