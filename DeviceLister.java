
import javax.sound.midi.*;


public class DeviceLister {

	public static void main(String[] args) throws Exception {

		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();

		for (int i = 0;  i < infos.length;  ++i) {
			MidiDevice.Info info = infos[i];
			MidiDevice device = MidiSystem.getMidiDevice(info);
			if (device.getReceiver() == null) {
				continue;
			}

			System.out.println("\"" + info.getName() + "\"");
			System.out.println("\tvendor: " + info.getVendor());
			System.out.println("\tversion: " + info.getVersion());
			System.out.println("\tdescription: " + info.getDescription());
			if (device instanceof Sequencer) {
				System.out.println("\t\tis a sequencer");
			}
			if (device instanceof Synthesizer) {
				System.out.println("\t\tis a synthesizer");
			}
		}

		System.exit(0);
	}
}


