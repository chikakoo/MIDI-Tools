@echo off

REM -p for pitch bend adjustments (default value assuming a range of 2)
REM -v for vibrato adjustments (default range of 5)
REM -r for reverb adjustments (default range of 26)
REM -c to clean up pitch bends to a tolerance of 16
java -jar MIDITools.jar "%~1" -p -v -r -c pitch-bend 16

pause