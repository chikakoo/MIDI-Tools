@echo off

REM -p for pitch bend adjustments (default value assuming a range of 2)
REM -v for vibrato adjustments (default range of 5)
REM -v to clean up pitch bends to a tolerance of 16
java MIDITools "%~1" -p -v -c pitch-bend 16

pause