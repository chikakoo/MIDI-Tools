# MIDI-Tools
Command line tool to help with MIDI adjustments when making Ocarina of Time sequence files.

# Quick Start
- Download the .zip from Releases
- Extract the contents somewhere
- Modify the .bat file with your desired flags/params (see below)
- Drag a MIDI onto the .bat file to execute
- It will output to <midi file name>.out.mid, overwriting any file with that name already

# Usage
**usage: [midi filename] [--verbose (optional)] [a list of flags and their parameters]**
- Runs all given parameter transformations in the order given.

**-p (pitch bend) [default range = 2]**
- Adjusts all pitch bend events by the given default range
- Automatically detects and uses the range in the midi if there is one

**-v (vibrato) [vibrato range = 5]**
- Adjusts all modulation events to be vibrato depth events instead
- Creates new events adjusted to the given range value
- For example, modulation of 127 would create a vibrato depth of 5

**-r (reverb) [reverb range = 26]**
- Adjusts all ReverbSendDepth events to the given range
- Creates new events adjusted to the given range value
- For example, a value of 127 would convert to a 5

**-c (clean up) [event number] [tolerance = 10]**
- Cleans up all events of the given number to be within the tolerance
- For example, events with values 10, 12, 14, 16, 18, 20
- Would be cleaned up to: 10, 20
- For pitch bends specifically, pass 'pitch-bend' for the event number

**-e (expression)**
- Replaces all expression events with volume events

**-a (add) [event number] [amount]**
- Adds the given amount from all instances of the given event
- For pitch bends specifically, pass 'pitch-bend' for the event number

**-s (subtract) [event number] [amount]**
- Subtracts the given amount from all instances of the given event
- For pitch bends specifically, pass 'pitch-bend' for the event number

**example: test.midi -p 3 -v -c 10 -a 77 1**
- Cleans up pitch bends to a default range of 3
- Replaces modulation events with vibrato depth, with the default range
- Cleans up panpot events to a tolerance of 10
- Adds 1 to each vibrato depth event
