#!/bin/sh
echo "Step 1: HTML Parsing"
java -jar target/DotGen-1.0-SNAPSHOT-jar-with-dependencies.jar OGCOMV3Draft.html OGCOMV3Draft
echo "Step 2: Dot"
dot -Tpng OGCOMV3Draft_obs.dot -o OGCOMV3Draft_obs.png
dot -Tsvg OGCOMV3Draft_obs.dot -o OGCOMV3Draft_obs.svg
dot -Tsvg OGCOMV3Draft_none.dot -o OGCOMV3Draft_none.svg
dot -Tpng OGCOMV3Draft_none.dot -o OGCOMV3Draft_none.png
dot -Tpng OGCOMV3Draft_sam.dot -o OGCOMV3Draft_sam.png
dot -Tsvg OGCOMV3Draft_sam.dot -o OGCOMV3Draft_sam.svg
echo "Step 3: Copy to Owncloud"
cp OGCOMV3Draft_*.png OGCOMV3Draft_*.svg OGCOMV3Draft_*.html ~/ownCloud/MyShare/OandM/
echo "Step 4: Done"


#cp OGCOMV3Draft_*.png OGCOMV3Draft_*.svg ../om-swg/docs/ISO/
#cd ../om-swg
#gitg 
#git push
#cd ../DotGen
