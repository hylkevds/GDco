#!/bin/bash
echo "Step 1: HTML Parsing"
INPUT='../20-082r2_OGC_Abstract_Specification_Topic_20_-_Observations_and_measurements.htm'
OUTPUT='OMSv3'
java -jar target/DotGen-1.0-SNAPSHOT-jar-with-dependencies.jar $INPUT output/${OUTPUT}
echo "Step 2: Dot"
cd output
for i in *.dot;
  do
    echo "  ${i}"
    dot -Tpng $i -o png/${i:0:-4}.png
    dot -Tsvg $i -o svg/${i:0:-4}.svg
  done
cd ..
echo "Step 3: Copy to Owncloud"
cp output/png/*.png output/svg/*.svg output/${OUTPUT}_*.html ~/ownCloud/MyShare/OandM/
echo "Step 4: Done"


#cp OGCOMV3Draft_*.png OGCOMV3Draft_*.svg ../om-swg/docs/ISO/
#cd ../om-swg
#gitg 
#git push
#cd ../DotGen
