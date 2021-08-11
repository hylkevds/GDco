#!/bin/bash
echo "Step 1: HTML Parsing"
OUTPUT='OMSv3'

java -jar target/DotGen-1.0-SNAPSHOT-jar-with-dependencies.jar configOms.json
echo "Step 2: Dot"
cd output/${OUTPUT}
for i in *.dot;
  do
    echo "  ${i}"
    dot -Tpng $i -o png/${i:0:-4}.png
    dot -Tsvg $i -o svg/${i:0:-4}.svg
  done
cd ../..
echo "Step 4: Done"
