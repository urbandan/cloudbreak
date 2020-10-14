#!/bin/bash
: <<USAGE
========================================================
this script is intended to be run in a docker container
========================================================
docker run -it --rm \
  --net=container:infrastructure-mock
  --entrypoint /bin/bash \
  hortonworks/infrastructure-mock -c /wait_for_infrastructure_mock_api.sh
USAGE


url="http://127.0.0.1:10090/"
maxAttempts=10
pollTimeout=30

cat <<EOF
========================================================
= echo this container waits for cloudbreak availabilty =
= by checking the health url:
=   $url
=
= maxAttempts=$maxAttempts
========================================================
EOF

for (( i=1; i<=$maxAttempts; i++ ))
do
    echo "GET $url. Attempt #$i"
    code=`curl -sL -w "%{http_code}\\n" "$url" -o /dev/null`
    echo "Found code $code"
    if [ "x$code" = "x200" ]
    then
         echo "Infrastructure mock is available!"
         break
    elif [ $i -eq $maxAttempts ]
    then
         echo "Infrastructure mock not started in time."
         exit 1
    fi
    sleep $pollTimeout
done