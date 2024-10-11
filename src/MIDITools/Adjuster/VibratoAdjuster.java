package MIDITools.Adjuster;

import MIDITools.MIDITools;

import javax.sound.midi.*;
import java.util.ArrayList;

public class VibratoAdjuster extends MIDIAdjuster {
    private static final int VIBRATO_DEPTH_EVENT = 77;
    private static final int MODULATION_EVENT = 1;
    private static final int MAX_VIBRATO_VALUE = 127;

    /**
     * Converts modulation events into vibrato depth events (that's what seq 64 uses)
     * - Will convert it to the closest value to the defaultVibratoRange (rounded up)
     * - For example, a range of 5 and a modulation of 10 will give a vibrato depth of 1
     * @param sequence - The sequence to modify
     */
    public static void editMidiVibrato(Sequence sequence) {
        ArrayList<String> vibratoChannelsAdded = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            if (!checkNeedToAddVibratoEventsAndDeleteIfSo(track)) {
                continue;
            }

            boolean addedVibratoEventAtBeginning = false;
            int lastVibratoAdded = -1;
            int lastChannel = -1;
            for (int i = 0; i < track.size(); i++) {
                boolean isLastItem = i + 1 >= track.size();
                MidiEvent e = track.get(i);
                MidiMessage msg = e.getMessage();

                if (msg instanceof ShortMessage) {
                    ShortMessage shortMsg = (ShortMessage) msg;
                    int command = shortMsg.getCommand();
                    int channel = shortMsg.getChannel();
                    lastChannel = channel;

                    if (command == ShortMessage.CONTROL_CHANGE) {
                        int data1 = shortMsg.getData1();
                        int data2 = shortMsg.getData2();

                        if (data1 == MODULATION_EVENT) {
                            // Set the flag if we're adding a vibrato at the start so that we know
                            // NOT to insert a vibrato event of 0 later on
                            if (e.getTick() == 0) {
                                addedVibratoEventAtBeginning = true;
                            }

                            // Add a new event if needed
                            int vibratoDepth = (int)Math.ceil(MIDITools.defaultVibratoRange * ((double)data2 / (double)MAX_VIBRATO_VALUE));
                            if (lastVibratoAdded != vibratoDepth) {
                                // Only list the message once; do so before the first vibrato is added
                                if (lastVibratoAdded == -1) {
                                    vibratoChannelsAdded.add(String.valueOf(channel + 1));
                                }

                                lastVibratoAdded = vibratoDepth;
                                addVibratoToTrack(track, channel, vibratoDepth, e.getTick());
                            }
                        }
                    }
                }

                // If we're on the last item, insert a beginning vibrato message if we need it
                if (!addedVibratoEventAtBeginning && isLastItem && lastChannel != -1) {
                    addVibratoToTrack(track, lastChannel, 0, 0);
                    break; // Adding a new vibrato will increase the list, and we'll loop forever!
                }
            }
        }

        if (!vibratoChannelsAdded.isEmpty()) {
            showChannelsModifiedMessage(vibratoChannelsAdded, "Vibrato added to channels");
            System.out.println();
        }
    }

    /**
     * Checks whether we will be adding vibrato events and deletes any existing ones if we will be
     * - If there are no modulation events, no need to edit anything
     * - If there ARE vibrato events, we don't want to mess things up, so leave a message and also don't add anything
     * @param track - the track to check
     * @return True if we need to add vibrato events; false otherwise
     */
    private static boolean checkNeedToAddVibratoEventsAndDeleteIfSo(Track track) {
        ArrayList<MidiEvent> vibratoEventsToDelete = new ArrayList<>();

        boolean foundModulationEvent = false;
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            MidiMessage msg = e.getMessage();

            if (msg instanceof ShortMessage) {
                ShortMessage shortMsg = (ShortMessage) msg;
                int command = shortMsg.getCommand();

                if (command == ShortMessage.CONTROL_CHANGE) {
                    int data1 = shortMsg.getData1();
                    int data2 = shortMsg.getData2();

                    if (data1 == VIBRATO_DEPTH_EVENT) {
                        vibratoEventsToDelete.add(e);
                    } else if (data1 == MODULATION_EVENT && data2 > 0) {
                        foundModulationEvent = true;
                    }
                }
            }
        }

        if (foundModulationEvent) {
            // Only delete these if we're adding new ones
            deleteEventsFromTrack(track, vibratoEventsToDelete);
        }

        return foundModulationEvent;
    }

    /**
     * Adds a vibrato event to the midi
     * @param track - the track to add to
     * @param channel - the channel to add to
     * @param vibratoDepth - the amount to set the vibrato to
     * @param tick - when to set it
     */
    private static void addVibratoToTrack(Track track, int channel, int vibratoDepth, long tick) {
        ShortMessage vibratoDepthMessage = new ShortMessage();
        setShortMessage(vibratoDepthMessage, ShortMessage.CONTROL_CHANGE, channel, VIBRATO_DEPTH_EVENT, vibratoDepth);
        track.add(new MidiEvent(vibratoDepthMessage, tick));

        verboseLog("Added vibrato depth of " + vibratoDepth + " at tick " + tick, channel);
    }
}
