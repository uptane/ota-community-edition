
rm -fr ./docker-context/dist
mkdir -p ./docker-context/dist

quasar build 
cp -r ./dist/spa/* ./docker-context/dist