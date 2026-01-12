package MIDITools.Adjuster;

import javax.sound.midi.*;
import java.util.ArrayList;

public class EventMover extends MIDIAdjuster {

    private final ArrayList<Integer> eventsToAdjust = new ArrayList<>();
    private boolean movingPitchBend = false;
    private boolean movingProgramChange = false;

    /**
     * {@inheritDoc}
     * Expected usage: -m [space-delimited set of events]
     */
    @Override
    public int execute(String[] args, int currentIndex, Sequence sequence) {
        ArrayList<String> transformationArgs = getAllArgs(args, currentIndex);

        for (String arg : transformationArgs) {
            if (arg.equals(PITCH_BEND_ARG)) {
                movingPitchBend = true;
                continue;
            }

            if (arg.equals(PROGRAM_CHANGE_ARG)) {
                movingProgramChange = true;
                continue;
            }

            eventsToAdjust.add(Integer.parseInt(arg));
        }

        moveMidiEventsToStart(sequence);

        return currentIndex + transformationArgs.size() + 1;
    }

    /**
     * Moves the MIDI events to adjust to the start of the song
     * @param sequence - The sequence to adjust
     */
    private void moveMidiEventsToStart(Sequence sequence) {
        int numberOfMovedEvents = 0;

        for (Track track : sequence.getTracks()) {
            ArrayList<ShortMessage> messagesToMove = new ArrayList<>();
            ArrayList<MidiEvent> eventsToMove = new ArrayList<>();
            ArrayList<Integer> eventsLeftToMove = new ArrayList<>(eventsToAdjust);
            boolean needToMovePitchBend = movingPitchBend;
            boolean needToMoveProgramChange = movingProgramChange;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();

                long currentTick = e.getTick();
                if (currentTick > 0 && !needToMovePitchBend && !needToMoveProgramChange && eventsLeftToMove.isEmpty()) {
                    break; // Nothing left to move
                }

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int data1 = shortMsg.getData1();

                    if (currentTick == 0 && command == ShortMessage.NOTE_ON) {
                        messagesToMove.add(shortMsg);
                        eventsToMove.add(e);
                    } else if (command == ShortMessage.PITCH_BEND && needToMovePitchBend) {
                        needToMovePitchBend = false;
                        tryAddEventToMoveList(shortMsg, e, messagesToMove, eventsToMove);
                    } else if (command == ShortMessage.PROGRAM_CHANGE && needToMoveProgramChange) {
                        needToMoveProgramChange = false;
                        tryAddEventToMoveList(shortMsg, e, messagesToMove, eventsToMove);
                    } else if (command == ShortMessage.CONTROL_CHANGE && eventsLeftToMove.contains(data1)) {
                        eventsLeftToMove.remove((Integer)data1);
                        tryAddEventToMoveList(shortMsg, e, messagesToMove, eventsToMove);
                    }
                }
            }

            for (ShortMessage msg : messagesToMove) {
                numberOfMovedEvents++;
                duplicateShortMessage(track, msg);
            }

            if (!messagesToMove.isEmpty()) {
                deleteEventsFromTrack(track, eventsToMove);
            }
        }

        if (numberOfMovedEvents == 0) {
            System.out.println("Did not move any events.");
        } else {
            System.out.println("Moved " + numberOfMovedEvents + " events.");
        }
    }

    /**
     * Tries to add the given event to the list of events to move
     * We should not add these if they are already at tick 0, as it could cause them
     * to be placed AFTER a note on event, which causes issues
     * @param shortMsg - The short message of the event
     * @param midiEvent - The midi event itself - contains the tick data
     * @param messagesToMove - The array of messages to move to add to
     * @param eventsToMove - The array of events to move
     */
    private static void tryAddEventToMoveList(
            ShortMessage shortMsg,
            MidiEvent midiEvent,
            ArrayList<ShortMessage> messagesToMove,
            ArrayList<MidiEvent> eventsToMove) {
        if (midiEvent.getTick() > 0) {
            messagesToMove.add(shortMsg);
            eventsToMove.add(midiEvent);
        }
    }

    /**
     * Duplicates the given event to the start of the track
     * @param track - The track to add to
     * @param msg - The short message to add a copy of
     */
    private static void duplicateShortMessage(
            Track track,
            ShortMessage msg) {
        int command = msg.getCommand();
        int channel = msg.getChannel();
        int data1 = msg.getData1();
        int data2 = msg.getData2();

        ShortMessage shortMessage = new ShortMessage();
        setShortMessage(shortMessage, command, channel, data1, data2);
        track.add(new MidiEvent(shortMessage, 0));
    }
}
