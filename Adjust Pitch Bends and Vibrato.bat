@echo off

REM Run the command with no args for more detailed usage instructions

REM Usage: usage: [midi filename] [--verbose (optional)] [a list of flags and their parameters]
REM -p (pitch bend) [default range = 2]"
REM -v (vibrato) [vibrato range = 5]
REM -r (reverb) [reverb range = 26]
REM -c (clean up) [event number] [tolerance = 10]
REM -a (add) [event number] [amount]
REM -s (subtract) [event number] [amount]

REM --------------------------------------------------------------------
REM For reference, commonly used event numbers for the -c, -a, and -s flags:
REM - pitch-bend (for pitch bends - this is the only one that doesn't use a number)
REM - 7 (Channel Volume)
REM - 10 (Panpot - l/r panning)
REM - 77 (VibratoDepth - vibrato in OoT)
REM - 91 (ReverbSendLevel - reverb in OoT)

REM --------------------------------------------------------------------
REM -p for pitch bend adjustments (default value assuming a range of 2)
REM -v for vibrato adjustments (default range of 5)
REM -r for reverb adjustments (default range of 26)
REM -c to clean up pitch bends to a tolerance of 16
java -jar MIDITools.jar "%~1" -p -v -r -c pitch-bend 16

pause