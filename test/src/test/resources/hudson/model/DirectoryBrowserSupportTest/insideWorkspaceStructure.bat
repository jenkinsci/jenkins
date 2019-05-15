mklink /D to_internal1 .\asset
mklink to_internal_goal1 .\asset\goal.txt

mkdir intermediateFolder
cd intermediateFolder
mklink /D to_internal2 ..\asset
mklink to_internal_goal2 ..\asset\goal.txt
