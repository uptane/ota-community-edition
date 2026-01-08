
PACKAGE_VERSION=$(cat package.json \
    | grep version \
    | head -1 \
    | awk -F: '{ print $2 }' \
    | sed 's/[",]//g' \
| tr -d '[[:space:]]')

PACKAGE_NAME=$(cat package.json \
    | grep name \
    | head -1 \
    | awk -F' ' '{ print $2 }' \
    | sed 's/[",]//g' \
| tr -d '[[:space:]]')

REPOSITORY_HOST=artifactory-horw.int.toradex.com
REPOSITORY_KEY=ota-docker-dev-horw
VERSION_TAG="$REPOSITORY_HOST/$REPOSITORY_KEY/$PACKAGE_NAME:$PACKAGE_VERSION"
LATEST_TAG="$REPOSITORY_HOST/$REPOSITORY_KEY/$PACKAGE_NAME:latest"


function Login
{
    cat ./src/secrets/docker-login.txt | docker login -u cicd-innovation --password-stdin $REPOSITORY_HOST 
}
function Build
{
    echo Building container image with tag $VERSION_TAG;
    Login
    docker build . -t $VERSION_TAG
}

function Push
{
    echo Pushing container image $VERSION_TAG;
    docker push $VERSION_TAG
}

function Publish
{
    echo Publishing container image $VERSION_TAG;
    Build
    Push
}
function Usage
{
    echo "Usage: docker-builder.sh build | push | publish"
    echo "     build -- Builds docker image for this project"
    echo "     push -- Pushes previously built docker image for this project to the registory"
    echo "     publish -- Builds and also pushes previously built docker image for this project to the registory"
    echo "     help | --help | -h -- Shows this information"
    
}
function Help
{
    echo ""
    echo "------------------------------------------------"
    echo "Docker Image Builder"
    echo "------------------------------------------------"
    Usage
    echo "------------------------------------------------"
    echo ""
}
function Invalid
{
    echo ""
    echo "Invalid option $1"
    echo ""
    Usage
    echo ""
    echo ""
}


PARAM=$1
if [[ $PARAM == build ]] 
then
   Build
elif  [[ $PARAM == push ]]
then
    Push
elif  [[ $PARAM == publish ]]
then
    Publish
elif  [[ $PARAM == help ]] || [[ $PARAM == --help ]] || [[ $PARAM == -h ]]
then
    Help
else
    Invalid $PARAM
fi
