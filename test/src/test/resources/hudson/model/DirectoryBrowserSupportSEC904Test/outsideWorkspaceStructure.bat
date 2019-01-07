echo public-1 > public1.key
mklink /D to_secrets1 ..\..\secrets
mklink to_secrets_goal1 ..\..\secrets\goal.txt

mkdir intermediateFolder
cd intermediateFolder
echo public-2 > public2.key
mklink /D to_secrets2 ..\..\..\secrets
mklink to_secrets_goal2 ..\..\..\secrets\goal.txt

mkdir otherFolder
cd otherFolder
mklink /D to_secrets3 ..\..\..\..\secrets
