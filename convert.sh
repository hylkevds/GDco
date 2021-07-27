#!/bin/bash
echo "Step 1: HTML Parsing"
INPUT='../20-082r2.html'
OUTPUT='OMSv3'

java -jar target/DotGen-1.0-SNAPSHOT-jar-with-dependencies.jar $INPUT output/${OUTPUT}/${OUTPUT}
echo "Step 2: Dot"
cd output/${OUTPUT}
for i in *.dot;
  do
    echo "  ${i}"
    dot -Tpng $i -o png/${i:0:-4}.png
    dot -Tsvg $i -o svg/${i:0:-4}.svg
  done
cd ../..
echo "Step 3: Copy to Owncloud"
cp output/${OUTPUT}/png/*.png output/${OUTPUT}/svg/*.svg output/${OUTPUT}/${OUTPUT}_*.html output/${OUTPUT}/${OUTPUT}.ttl ~/ownCloud/MyShare/StaV2/
echo "Step 4: Done"

