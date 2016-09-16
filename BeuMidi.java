
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.ScheduledCancellable;
import java.util.concurrent.ScheduledExecutor;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;

import org.xml.sax.SAXParseException;


public class BeuMidi {

	static BeuMidi instance;

	public static void main(String[] args) {

		if (args.length < 1) {
			System.err.println("Usage: java BeuMidi BEUMIDI.xml ...");
			System.exit(1);
		}

		instance = null;
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				BeuMidi.terminate();
			}
		});

		try {
			instance = new BeuMidi();
			instance.convert(args[0]);
		} catch (SAXParseException e) {
			System.err.printf("Line:%d, Column %d\n",
					e.getLineNumber(), e.getColumnNumber());
			System.err.println(e.toString());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	DocumentBuilderFactory factory;
	DocumentBuilder builder;

	public BeuMidi() throws Exception {

		factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(true);
		factory.setNamespaceAware(true);
		factory.setAttribute(
				"http://java.sun.com/xml/jaxp/properties/schemaLanguage",
				"http://www.w3.org/2001/XMLSchema");

		builder = factory.newDocumentBuilder();
		builder.setErrorHandler(new ErrorHandler() {
			public void error(SAXParseException e) {
				exit(e, "Error");
			}
			public void fatalError(SAXParseException e) {
				exit(e, "FatalError");
			}
			public void warning(SAXParseException e) {
				exit(e, "Warning");
			}
			void exit(SAXParseException e, String sType) {
				System.err.printf("%s (line:%d, column:%d)\n",
						sType, e.getLineNumber(), e.getColumnNumber());
				System.err.println(e.getMessage());
				System.exit(1);
			}
		});
	}

	public void convert(String sFilePath) throws Exception {

		Document document = getDocument(sFilePath);
		mergeDefinitionElements(document);
		mergeMidiElements(document);
		mergeOutputElements(document);
		removeValueDefElements(document, "global");
		removeMacroDefElements(document, "global");
		removePhraseDefElements(document, "global");
		removeRepeatElements(document);
		removeValuesElements(document);
		removeNoteElements(document);
		removeTransposeElements(document);
		explicateImplicitRest(document);
		removePhraseElements0(document);
		removeParallelElements(document);
		removePhraseElements(document);
		removeMilestoneElements(document);
		mergeContinuousRestElements(document);
		adjustNoteOnOffElements(document);
Util.debugDocument(document);

		documentToTracks(document);
	}

	Document getDocument(String sFilePath) throws Exception {

		System.out.printf("file: %s\n", sFilePath);

		Document document = builder.parse(new File(sFilePath));
		removeUnnecessaryNodes(document.getDocumentElement());
		setDefaultAttributeValues(document);
		removeIgnoreElements(document.getDocumentElement());
		removeValueDefElements(document, "local");
		removeMacroDefElements(document, "local");
		removePhraseDefElements(document, "local");
		removeImportElements(document);
		return document;
	}

	void removeUnnecessaryNodes(Element element) {

		Node child = element.getFirstChild();
		while (child != null) {
			Node next = child.getNextSibling();
			if (!(child instanceof Element)) {
				element.removeChild(child);
			} else {
				removeUnnecessaryNodes((Element)child);
			}
			child = next;
		}
	}

	void setDefaultAttributeValues(Document document) throws Exception {

		setDefaultAttributeValues(document, "value-def", "scope");
		setDefaultAttributeValues(document, "macro-def", "scope");
		setDefaultAttributeValues(document, "phrase-def", "scope");
		setDefaultAttributeValues(document, "values", "type");
		setDefaultAttributeValues(document, "system-exclusive", "cs-index");
	}

	void setDefaultAttributeValues(
			Document document,
			String sElement,
			String sAttribute)
			throws Exception {

		ArrayList<Element> elementList = getElements(document, sElement);
		for (int i = 0;  i < elementList.size();  ++i) {
			Element element = elementList.get(i);
			element.setAttribute(sAttribute, element.getAttribute(sAttribute));
		}
	}

	void removeIgnoreElements(Element element) {

		Node child = element.getFirstChild();
		while (child != null) {
			Node next = child.getNextSibling();
			if (((Element)child).getTagName().equals("ignore")) {
				element.removeChild(child);
			} else {
				removeIgnoreElements((Element)child);
			}
			child = next;
		}
	}

	void removeValueDefElements(Document document, String sScope) {

		ArrayList<Element> elementList
				= getElements(document, "value-def", "scope", sScope);
		for (int i = 0;  i < elementList.size();  ++i) {
			Element valueDefElement = elementList.get(i);
			String sElement = valueDefElement.getAttribute("element");
			String sAttribute = valueDefElement.getAttribute("attribute");
			String sName = valueDefElement.getAttribute("name");
			String sValue = valueDefElement.getAttribute("value");
			ArrayList<Element> elementList2
					= getElements(document, sElement, sAttribute, sName);
			for (int j = 0;  j < elementList2.size();  ++j) {
				Element element2 = elementList2.get(j);
				element2.setAttribute(sAttribute, sValue);
			}
			valueDefElement.getParentNode().removeChild(valueDefElement);
		}
	}

	void removeMacroDefElements(Document document, String sScope)
			throws Exception {

		ArrayList<Element> elementList
				= getElements(document, "macro-def", "scope", sScope);
		for (int i = 0;  i < elementList.size();  ++i) {
			Element macroDefElement = elementList.get(i);
			String sName = macroDefElement.getAttribute("name");
			String[] sParameters = macroDefElement.getAttribute("parameter")
					.split("\\s");
			ArrayList<Element> elementList2
					= getElements(document, "macro-ref", "ref", sName);
			for (int j = 0;  j < elementList2.size();  ++j) {
				Element element2 = elementList2.get(j);
				String[] sValues = element2.getAttribute("value").split("\\s");
				if (sParameters.length != sValues.length) {
					throw new Exception("The number of values of macro-ref does not equal to that of macro-def (name=\"" + sName + "\").");
				}
				Element newElement = (Element)macroDefElement.cloneNode(true);
				substituteMacroParameters(newElement, sParameters, sValues);
				for (Node child = newElement.getFirstChild();  child != null;
						child = child.getNextSibling()) {
					element2.getParentNode()
							.insertBefore(child.cloneNode(true), element2);
				}
				element2.getParentNode().removeChild(element2);
			}
			macroDefElement.getParentNode().removeChild(macroDefElement);
		}
	}

	void substituteMacroParameters(
			Element element,
			String[] sParameters,
			String[] sValues) {

		for (Node child = element.getFirstChild();  child != null;
				child = child.getNextSibling()) {
			substituteMacroParameters((Element)child, sParameters, sValues);
		}
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0;  i < attributes.getLength();  ++i) {
			String sName = ((Attr)attributes.item(i)).getName();
			for (int j = 0;  j < sParameters.length;  ++j) {
				element.setAttribute(sName,
						element.getAttribute(sName)
						.replaceAll(sParameters[j], sValues[j]));
			}
		}
	}

	void removePhraseDefElements(Document document, String sScope) {

		ArrayList<Element> elementList
				= getElements(document, "phrase-def", "scope", sScope);
		for (int i = 0;  i < elementList.size();  ++i) {
			Element phraseDefElement = elementList.get(i);
			String sName = phraseDefElement.getAttribute("name");
			String sStep = phraseDefElement.getAttribute("step");
			ArrayList<Element> elementList2
					= getElements(document, "phrase-ref", "ref", sName);
			for (int j = 0;  j < elementList2.size();  ++j) {
				Element element2 = elementList2.get(j);
				Element newElement = document.createElement("phrase");
				newElement.setAttribute("step", sStep);
				for (Node child = phraseDefElement.getFirstChild();
						child != null;  child = child.getNextSibling()) {
					newElement.appendChild(child.cloneNode(true));
				}
				element2.getParentNode().replaceChild(newElement, element2);
			}
			Element newElement = document.createElement("phrase");
			newElement.setAttribute("step", sStep);
			for (Node child = phraseDefElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				newElement.appendChild(child.cloneNode(true));
			}
			phraseDefElement.getParentNode()
					.replaceChild(newElement, phraseDefElement);
		}
	}

	void removeImportElements(Document document) throws Exception {

		ArrayList<Element> elementList
				= getElements(document, "import");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element importElement = elementList.get(i);
			Document document2
					= getDocument(importElement.getAttribute("file"));
			Element beumidiElement = document2.getDocumentElement();
			for (Node child = beumidiElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				importElement.getParentNode().insertBefore(
						document.importNode(child, true), importElement);
			}
			importElement.getParentNode().removeChild(importElement);
		}
	}

	void mergeDefinitionElements(Document document) {

		ArrayList<Element> elementList = getElements(document, "definition");
		for (int i = 1;  i < elementList.size();  ++i) {
			Element element = elementList.get(i);
			for (Node child = element.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				elementList.get(0).appendChild(child.cloneNode(true));
			}
			element.getParentNode().removeChild(element);
		}
	}

	void mergeMidiElements(Document document) {

		ArrayList<Element> elementList = getElements(document, "midi");
		for (int i = 1;  i < elementList.size();  ++i) {
			Element element = elementList.get(i);
			for (Node child = element.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				elementList.get(0).appendChild(child.cloneNode(true));
			}
			element.getParentNode().removeChild(element);
		}

		Hashtable<String, Element> trackHash
				= new Hashtable<String, Element>();
		elementList = getElements(document, "track");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element trackElement = elementList.get(i);
			String sName = trackElement.getAttribute("name");
			Element trackElement0 = trackHash.get(sName);
			if (trackElement0 != null) {
				for (Node child = trackElement.getFirstChild();  child != null;
						child = child.getNextSibling()) {
					trackElement0.appendChild(child.cloneNode(true));
				}
				trackElement.getParentNode().removeChild(trackElement);
			} else {
				trackHash.put(sName, trackElement);
			}
		}
	}

	void mergeOutputElements(Document document) {

		ArrayList<Element> elementList = getElements(document, "output");
		for (int i = 1;  i < elementList.size();  ++i) {
			Element element = elementList.get(i);
			for (Node child = element.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				elementList.get(0).appendChild(child.cloneNode(true));
			}
			element.getParentNode().removeChild(element);
		}

		Hashtable<String, Element> deviceHash
				= new Hashtable<String, Element>();
		elementList = getElements(document, "device");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element deviceElement = elementList.get(i);
			String sName = deviceElement.getAttribute("name");
			Element deviceElement0 = deviceHash.get(sName);
			if (deviceElement0 != null) {
				for (Node child = deviceElement.getFirstChild();
						child != null;  child = child.getNextSibling()) {
					deviceElement0.appendChild(child.cloneNode(true));
				}
				deviceElement.getParentNode().removeChild(deviceElement);
			} else {
				deviceHash.put(sName, deviceElement);
			}
		}
	}

	void removeRepeatElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "repeat");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element repeatElement = elementList.get(i);
			long times = (new Value(repeatElement.getAttribute("times")))
					.getValue();
			for (;  times > 0;  --times) {
				for (Node child = repeatElement.getFirstChild();
						child != null;  child = child.getNextSibling()) {
					repeatElement.getParentNode().insertBefore(
							child.cloneNode(true), repeatElement);
				}
			}
			repeatElement.getParentNode().removeChild(repeatElement);
		}
	}

	void removeValuesElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "values");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element valuesElement = elementList.get(i);
			String sType = valuesElement.getAttribute("type");
			Element phraseElement = null;
			if (sType.equals("straight")) {
				phraseElement = valuesElementStraightToPhraseElement(
						document, valuesElement);
			} else {
				throw new Exception("Unsupported type: " + sType);
			}
			valuesElement.getParentNode()
					.replaceChild(phraseElement, valuesElement);
		}
	}

	Element valuesElementStraightToPhraseElement(
			Document document,
			Element valuesElement)
			throws Exception {

		String sParameter = valuesElement.getAttribute("parameter");
		long startValue = (new Value(valuesElement.getAttribute("start")))
				.getValue();
		long endValue = (new Value(valuesElement.getAttribute("end")))
				.getValue();
		long times = (new Value(valuesElement.getAttribute("times")))
				.getValue();
		String sStep = valuesElement.getAttribute("step");
		long step = (new Value(sStep)).getValue();

		Element phraseElement = document.createElement("phrase");
		phraseElement.setAttribute("step", sStep);

		long valueDiff = (endValue - startValue) / times;
		long value = startValue - valueDiff;
		long stepDiff = step / times;
		long stepRemainder = step;
		for (;  times > 0;  --times) {
			if (times > 1) {
				step = stepDiff;
				stepRemainder -= stepDiff;
				value += valueDiff;
			} else {
				step = stepRemainder;
				value = endValue;
			}
			Element phraseElement2 = document.createElement("phrase");
			phraseElement2.setAttribute("step", Long.toString(step));
			for (Node child = valuesElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				Element element = (Element)child.cloneNode(true);
				String[] sParameters = { sParameter };
				String[] sValues = { Long.toString(value) };
				substituteMacroParameters(element, sParameters, sValues);
				phraseElement2.appendChild(element);
			}
			phraseElement.appendChild(phraseElement2);
		}

		return phraseElement;
	}

	void removeNoteElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "note");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element noteElement = elementList.get(i);

			String sNote = noteElement.getAttribute("note");
			for (Node prev = noteElement.getPreviousSibling();
					sNote.equals("") && prev != null;
					prev = prev.getPreviousSibling()) {
				Element element = (Element)prev;
				if (element.getTagName().equals("note")) {
					sNote = element.getAttribute("note");
				}
			}
			if (sNote.equals("")) {
				throw new Exception("note attribute of note is empty.");
			}
			noteElement.setAttribute("note", sNote);

			String sStep = noteElement.getAttribute("step");
			for (Node prev = noteElement.getPreviousSibling();
					sStep.equals("") && prev != null;
					prev = prev.getPreviousSibling()) {
				Element element = (Element)prev;
				if (element.getTagName().equals("note")) {
					sStep = element.getAttribute("step");
				}
			}
			if (sStep.equals("")) {
				throw new Exception("step attribute of note is empty.");
			}
			noteElement.setAttribute("step", sStep);

			String sGate = noteElement.getAttribute("gate");
			for (Node prev = noteElement.getPreviousSibling();
					sGate.equals("") && prev != null;
					prev = prev.getPreviousSibling()) {
				Element element = (Element)prev;
				if (element.getTagName().equals("note")) {
					sGate = element.getAttribute("gate");
				}
			}
			if (sGate.equals("")) {
				throw new Exception("gate attribute of note is empty.");
			}
			noteElement.setAttribute("gate", sGate);

			String sVelocity = noteElement.getAttribute("velocity");
			for (Node prev = noteElement.getPreviousSibling();
					sVelocity.equals("") && prev != null;
					prev = prev.getPreviousSibling()) {
				Element element = (Element)prev;
				if (element.getTagName().equals("note")) {
					sVelocity = element.getAttribute("velocity");
				}
			}
			if (sVelocity.equals("")) {
				throw new Exception("velocity attribute of note is empty.");
			}
			noteElement.setAttribute("velocity", sVelocity);

			String sOffVelocity = noteElement.getAttribute("off-velocity");
			if (sOffVelocity.equals("")) {
				sOffVelocity = sVelocity;
			}
			noteElement.setAttribute("off-velocity", sOffVelocity);
		}

		for (int i = 0;  i < elementList.size();  ++i) {
			Element noteElement = elementList.get(i);

			long note = (new Value(noteElement.getAttribute("note")))
					.getValue();
			long step = (new Value(noteElement.getAttribute("step")))
					.getValue();
			long gate = (new Value(noteElement.getAttribute("gate")))
					.getValue();
			String sVelocity = noteElement.getAttribute("velocity");
			String sOffVelocity = noteElement.getAttribute("off-velocity");

			Element phraseElement = document.createElement("phrase");
			phraseElement.setAttribute("step", Long.toString(step));

			Element noteOnElement = document.createElement("note-on");
			noteOnElement.setAttribute("note", Long.toString(note));
			noteOnElement.setAttribute("velocity", sVelocity);
			phraseElement.appendChild(noteOnElement);

			Element restElement = document.createElement("rest");
			restElement.setAttribute("step",
					Long.toString(Math.min(step, gate)));
			phraseElement.appendChild(restElement);

			if (step >= gate) {
				Element noteOffElement = document.createElement("note-off");
				noteOffElement.setAttribute("note", Long.toString(note));
				noteOffElement.setAttribute("velocity", sOffVelocity);
				phraseElement.appendChild(noteOffElement);

				if (step > gate) {
					restElement = document.createElement("rest");
					restElement.setAttribute("step",
							Long.toString(step - gate));
					phraseElement.appendChild(restElement);
				}
			}
			noteElement.getParentNode()
					.replaceChild(phraseElement, noteElement);
		}
	}

	void removeTransposeElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "transpose");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element transposeElement = elementList.get(i);

			long value = Long.parseLong(
					transposeElement.getAttribute("value"));
			transpose(transposeElement, value);
		}
	}

	void transpose(Element element, long value) throws Exception {

		for (Node child = element.getFirstChild();  child != null;
				child = child.getNextSibling()) {
			transpose((Element)child, value);
		}
		String sNote = element.getAttribute("note");
		if (!sNote.equals("")) {
			long note = (new Value(sNote)).getValue();
			element.setAttribute("note", Long.toString(note + value));
		}
	}

	void explicateImplicitRest(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "phrase");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element phraseElement = elementList.get(i);
			long step0 = getStep(phraseElement);
			long step = 0;
			for (Node child = phraseElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				step += getStep((Element)child);
			}
			if (step0 > step) {
				Element restElement = document.createElement("rest");
				restElement.setAttribute("step", Long.toString(step0 - step));
				phraseElement.appendChild(restElement);
				phraseElement.setAttribute("step", Long.toString(step0));
			} else if (step0 == step) {
				phraseElement.setAttribute("step", Long.toString(step0));
			} else {
				throw new Exception("step " + Long.toString(step0) + " of phrase element does not equal to that of the children " + Long.toString(step));
			}
		}

		elementList = getElements(document, "parallel");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element parallelElement = elementList.get(i);
			long step0 = getStep(parallelElement);
			for (Node child = parallelElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				Element element = (Element)child;
				long step = getStep(element);
				if (step0 > step) {
					Element phraseElement = document.createElement("phrase");
					phraseElement.setAttribute("step", Long.toString(step0));
					Element restElement = document.createElement("rest");
					restElement.setAttribute("step",
							Long.toString(step0 - step));
					phraseElement.appendChild(element.cloneNode(true));
					phraseElement.appendChild(restElement);
					parallelElement.replaceChild(phraseElement, element);
				}
			}
		}

		elementList = getElements(document, "midi");
		Element midiElement = elementList.get(0);
		long step0 = getStep(midiElement);
		elementList = getElements(document, "track");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element trackElement = elementList.get(i);
			long step = getStep(trackElement);
			if (step0 > step) {
				Element restElement = document.createElement("rest");
				restElement.setAttribute("step", Long.toString(step0 - step));
				trackElement.appendChild(restElement);
			}
		}
	}

	long getStep(Element element) throws Exception {

		String sTagName = element.getTagName();
		if (sTagName.equals("phrase") || sTagName.equals("track")) {
			if (element.getAttribute("step").equals("")) {
				long step = 0;
				for (Node child = element.getFirstChild();  child != null;
						child = child.getNextSibling()) {
					step += getStep((Element)child);
				}
				return step;
			} else {
				return (new Value(element.getAttribute("step"))).getValue();
			}
		} else if (sTagName.equals("parallel") || sTagName.equals("midi")) {
			long maxStep = 0;
			for (Node child = element.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				long step = getStep((Element)child);
				maxStep = Math.max(maxStep, step);
			}
			return maxStep;
		} else if (sTagName.equals("rest")) {
			return (new Value(element.getAttribute("step"))).getValue();
		}
		return 0;
	}

	void removeParallelElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "parallel");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element parallelElement = elementList.get(i);
			TreeMap<Long, ArrayList<Element>> offsetMap
					= new TreeMap<Long, ArrayList<Element>>();
			for (Node child = parallelElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				Element element = (Element)child;
				if (!element.getTagName().equals("phrase")) {
					ArrayList<Element> list = offsetMap.get(0L);
					if (list == null) {
						list = new ArrayList<Element>();
					}
					list.add(element);
					offsetMap.put(0L, list);
				} else {
					long offset = 0;
					for (Node child2 = element.getFirstChild();
							child2 != null;
							child2 = child2.getNextSibling()) {

						ArrayList<Element> list = offsetMap.get(offset);
						if (list == null) {
							list = new ArrayList<Element>();
						}
						if (!((Element)child2).getTagName().equals("rest")) {
							list.add((Element)child2);
						}
						offsetMap.put(offset, list);
						offset += getStep((Element)child2);
					}
				}
			}
			Element phraseElement = document.createElement("phrase");
			long phraseOffset = 0;
			Iterator<Long> iter = offsetMap.keySet().iterator();
			while (iter.hasNext()) {
				long offset = iter.next();
				if (offset > phraseOffset) {
					Element restElement = document.createElement("rest");
					restElement.setAttribute("step",
							Long.toString(offset - phraseOffset));
					phraseElement.appendChild(restElement);
					phraseOffset = offset;
				}
				ArrayList<Element> list = offsetMap.get(offset);
				for (int j = 0;  j < list.size();  ++j) {
					phraseElement.appendChild(list.get(j).cloneNode(true));
				}
			}
			long step0 = getStep(parallelElement);
			long step = getStep(phraseElement);
			if (step0 > step) {
				Element restElement = document.createElement("rest");
				restElement.setAttribute("step", Long.toString(step0 - step));
				phraseElement.appendChild(restElement);
			}
			phraseElement.setAttribute("step", Long.toString(step0));
			parallelElement.getParentNode()
					.replaceChild(phraseElement, parallelElement);
		}
	}

	void removePhraseElements0(Document document) {

		ArrayList<Element> elementList = getElements(document, "phrase");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element phraseElement = elementList.get(i);
			if (((Element)(phraseElement.getParentNode()))
					.getTagName().equals("parallel")) {
				continue;
			}
			for (Node child = phraseElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				phraseElement.getParentNode().insertBefore(
						child.cloneNode(true), phraseElement);
			}
			phraseElement.getParentNode().removeChild(phraseElement);
		}
	}

	void removePhraseElements(Document document) {

		ArrayList<Element> elementList = getElements(document, "phrase");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element phraseElement = elementList.get(i);
			for (Node child = phraseElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				phraseElement.getParentNode().insertBefore(
						child.cloneNode(true), phraseElement);
			}
			phraseElement.getParentNode().removeChild(phraseElement);
		}
	}

	void removeMilestoneElements(Document document) throws Exception {

		Hashtable<String, Long> offsetHash = new Hashtable<String, Long>();
		ArrayList<Element> elementList = getElements(document, "track");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element trackElement = elementList.get(i);
			long offset = 0;
			Node child = trackElement.getFirstChild();
			while (child != null) {
				Node next = child.getNextSibling();

				Element element = (Element)child;
				long step = getStep(element);
				if (element.getTagName().equals("milestone")) {
					String sName = element.getAttribute("name");
					if (offsetHash.get(sName) == null) {
						offsetHash.put(sName, offset);
					} else {
						if (offset != offsetHash.get(sName)) {
							throw new Exception("milestone element (name=\""
									+ sName + "\") have different offset.");
						}
					}
					trackElement.removeChild(element);
				}
				offset += step;

				child = next;
			}
		}
	}

	void adjustNoteOnOffElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "track");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element trackElement = elementList.get(i);
			Hashtable<Long, Element> noteHash = new Hashtable<Long, Element>();

			Node child = trackElement.getFirstChild();
			while (child != null) {
				Node next = child.getNextSibling();

				Element element = (Element)child;
				String sTagName = element.getTagName();
				if (sTagName.equals("note-on")) {
					long note = (new Value(element.getAttribute("note")))
							.getValue();
					if (noteHash.get(note) == null) {
						noteHash.put(note, element);
					} else {
						trackElement.removeChild(element);
					}
				} else if (sTagName.equals("note-off")) {
					long note = (new Value(element.getAttribute("note")))
							.getValue();
					if (noteHash.get(note) == null) {
						trackElement.removeChild(element);
					} else {
						noteHash.remove(note);
					}
				}

				child = next;
			}
			Enumeration<Long> e = noteHash.keys();
			while (e.hasMoreElements()) {
				long note = e.nextElement();
				Element noteOffElement = document.createElement("note-off");
				noteOffElement.setAttribute("note", Long.toString(note));
				noteOffElement.setAttribute("velocity",
						noteHash.get(note).getAttribute("velocity"));
				trackElement.appendChild(noteOffElement);
			}
		}
	}

	void mergeContinuousRestElements(Document document) throws Exception {

		ArrayList<Element> elementList = getElements(document, "track");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element trackElement = elementList.get(i);
			for (Node child = trackElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {

				Element element = (Element)child;
				if (!element.getTagName().equals("rest")) {
					continue;
				}
				long step = (new Value(element.getAttribute("step")))
						.getValue();

				while (element.getNextSibling() != null
						&& ((Element)element.getNextSibling()).getTagName()
						.equals("rest")) {
					step += (new Value(((Element)element.getNextSibling())
							.getAttribute("step"))).getValue();
					trackElement.removeChild(element.getNextSibling());
				}
				element.setAttribute("step", Long.toString(step));
			}
		}
	}

	void documentToTracks(Document document) throws Exception {

		Hashtable<String, Track[]> resultHash
				= new Hashtable<String, Track[]>();

		ArrayList<Element> elementList = getElements(document, "midi");
		if (elementList.size() < 1) {
			throw new Exception("midi element is not found.");
		}
		int tpqn = Integer.parseInt(elementList.get(0).getAttribute("tpqn"));
		Sequence sequence = new Sequence(Sequence.PPQ, tpqn);	// dummy
		Sequence sequence2 = null;	// for smf1

		elementList = getElements(document, "device");
		for (int i = 0;  i < elementList.size();  ++i) {
			Element deviceElement = elementList.get(i);
			String sName = deviceElement.getAttribute("name");
			if (sName.equals("")) {
				continue;
			}
			boolean isFile = sName.matches("smf[01]:.+");
			if (isFile) {
				sequence2 = new Sequence(Sequence.PPQ, tpqn);
			}
			ArrayList<Track> trackList = new ArrayList<Track>();
			for (Node child = deviceElement.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				Element channelElement = (Element)child;
				String sTrackName = channelElement.getAttribute("track");
				ArrayList<Element> trackElementList
						= getElements(document, "track", "name", sTrackName);
				if (trackElementList.size() < 1) {
					continue;
				}
				int channel = Integer.parseInt(
						channelElement.getAttribute("number"));
				Element trackElement = trackElementList.get(0);

				if (!isFile) {
					Track track = sequence.createTrack();
					trackElementToTrack(track, trackElement, channel);
					trackList.add(track);
				} else {
					Track track = sequence2.createTrack();
					trackElementToTrack(track, trackElement, channel);
				}
			}
			if (!isFile) {
				resultHash.put(sName, (Track[])
						trackList.toArray(new Track[trackList.size()]));
			} else {
				// save sequence2
				File file = new File(sName.substring(5));
				try {
					file.delete();
				} catch (Exception e) {
				}
				if (sName.indexOf("smf0:") == 0) {
					MidiSystem.write(sequence2, 0, file);
				} else if (sName.indexOf("smf1:") == 0) {
					MidiSystem.write(sequence2, 1, file);
				}
System.out.println("saved " + file + ".");
			}
		}
		if (resultHash.size() > 0) {
			playTracks(resultHash, tpqn);
		}
	}

	void trackElementToTrack(Track track, Element trackElement, int channel)
			throws Exception {

		long tick = 0;
		for (Node child = trackElement.getFirstChild();  child != null;
				child = child.getNextSibling()) {
			Element element = (Element)child;
			String sTagName = element.getTagName();
			MidiMessage message = null;

			if (sTagName.equals("rest")) {
				tick += (new Value(element.getAttribute("step"))).getValue();
				continue;
			} else if (sTagName.equals("note-off")) {
				int note = (int)(new Value(element.getAttribute("note")))
						.getValue();
				int velocity = (int)
						(new Value(element.getAttribute("velocity")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.NOTE_OFF, channel, note, velocity);
			} else if (sTagName.equals("note-on")) {
				int note = (int)(new Value(element.getAttribute("note")))
						.getValue();
				int velocity = (int)
						(new Value(element.getAttribute("velocity")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.NOTE_ON, channel, note, velocity);
			} else if (sTagName.equals("poly-pressure")) {
				int note = (int)(new Value(element.getAttribute("note")))
						.getValue();
				int value = (int)(new Value(element.getAttribute("value")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.POLY_PRESSURE, channel, note, value);
			} else if (sTagName.equals("control-change")) {
				int controller = (int)
						(new Value(element.getAttribute("controller")))
						.getValue();
				int value = (int)(new Value(element.getAttribute("value")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.CONTROL_CHANGE, channel,
						controller, value);
			} else if (sTagName.equals("program-change")) {
				int value = (int)(new Value(element.getAttribute("value")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.PROGRAM_CHANGE, channel, value, 0);
			} else if (sTagName.equals("channel-pressure")) {
				int value = (int)(new Value(element.getAttribute("value")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.CHANNEL_PRESSURE, channel, value, 0);
			} else if (sTagName.equals("pitch-bend")) {
				int value = (int)(new Value(element.getAttribute("value")))
						.getValue();
				message = new ShortMessage();
				((ShortMessage)message).setMessage(
						ShortMessage.PITCH_BEND, channel,
						value & 0x7f, (value >> 7) & 0x7f);
			} else if (sTagName.equals("system-exclusive")) {
				long csIndex = (new Value(element.getAttribute("cs-index")))
						.getValue();
				long[] data = (new ValueList(element.getAttribute("data"),
						channel, (int)csIndex)).getValues();
				byte[] bytes = new byte[1 + data.length + 1];
				bytes[0] = (byte)0xf0;
				int i = 0;
				for (;  i < data.length;  ++i) {
					bytes[1 + i] = (byte)(data[i] & 0xff);
				}
				bytes[1 + i] = (byte)0xf7;
				message = new SysexMessage();
				((SysexMessage)message).setMessage(bytes, bytes.length);
			} else if (sTagName.equals("meta")) {
				long[] data = (new ValueList(element.getAttribute("data")))
						.getValues();
				byte[] bytes = new byte[data.length];
				for (int i = 0;  i < data.length;  ++i) {
					bytes[i] = (byte)(data[i] & 0xff);
				}
				message = new MetaMessage();
				((MetaMessage)message).setMessage((int)
						(new Value(element.getAttribute("type"))).getValue(),
						bytes, bytes.length);
			} else if (sTagName.equals("text")) {
				byte[] bytes = element.getAttribute("text").getBytes();
				message = new MetaMessage();
				((MetaMessage)message).setMessage((int)
						(new Value(element.getAttribute("type"))).getValue(),
						bytes, bytes.length);
			} else {
				throw new Exception("Unknown tag name: " + sTagName);
			}
			MidiEvent event = new MidiEvent(message, tick);
			track.add(event);
		}
		MetaMessage message = new MetaMessage();
		message.setMessage(0x2f, null, 0);
		track.add(new MidiEvent(message, tick));
	}

	void playTracks(Hashtable<String, Track[]> tracksHash, int tpqn)
			throws Exception {

		int maxTracks = 0;
		Enumeration<String> e = tracksHash.keys();
		while (e.hasMoreElements()) {
			Track[] tracks = tracksHash.get(e.nextElement());
			maxTracks += tracks.length;
		}

		String[] sDeviceNames = new String[maxTracks];
		Track[] tracks = new Track[maxTracks];
		int i = 0;
		e = tracksHash.keys();
		while (e.hasMoreElements()) {
			String sDeviceName = e.nextElement();
			Track[] tracks2 = tracksHash.get(sDeviceName);
			for (int j = 0;  j < tracks2.length;  ++j) {
				sDeviceNames[i] = sDeviceName;
				tracks[i] = tracks2[j];
				++i;
			}
		}

		MultiDeviceMidiPlayer player
				= new MultiDeviceMidiPlayer(sDeviceNames, tracks, tpqn);
	}

	ArrayList<Element> getElements(
			Document document,
			String sElement,
			String sAttribute,
			String sValue) {

		ArrayList<Element> elementList = new ArrayList<Element>();
		Hashtable<String, String> attributeHash
				= new Hashtable<String, String>();
		attributeHash.put(sAttribute, sValue);
		getElements(elementList, document.getDocumentElement(),
				sElement, attributeHash);
		return elementList;
	}

	ArrayList<Element> getElements(Document document, String sElement) {

		ArrayList<Element> elementList = new ArrayList<Element>();
		Hashtable<String, String> attributeHash
				= new Hashtable<String, String>();
		getElements(elementList, document.getDocumentElement(),
				sElement, attributeHash);
		return elementList;
	}

	void getElements(
			ArrayList<Element> elementList,
			Element element,
			String sElement,
			Hashtable<String, String> attributeHash) {

		for (Node child = element.getFirstChild();  child != null;
				child = child.getNextSibling()) {
			getElements(elementList, (Element)child, sElement, attributeHash);
		}
		if (!element.getTagName().equals(sElement)) {
			return;
		}
		Enumeration<String> e = attributeHash.keys();
		while (e.hasMoreElements()) {
			String sKey = e.nextElement();
			if (!element.getAttribute(sKey).equals(attributeHash.get(sKey))) {
				return;
			}
		}
		elementList.add(element);
	}

	static void terminate() {
	}
}

class Util {

	public static void debugDocument(Document document) {
		debugElement(document.getDocumentElement(), 0);
	}

	public static void debugElement(Element element, int depth) {

		for (int i = 0;  i < depth;  ++i) {
			System.out.print("\t");
		}
		System.out.printf("<%s", element.getTagName());
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0;  i < attributes.getLength();  ++i) {
			Attr attribute = (Attr)attributes.item(i);
			System.out.printf(" %s=\"%s\"",
					attribute.getName(), attribute.getValue());
		}
		if (element.getFirstChild() != null) {
			System.out.println(">");
			for (Node child = element.getFirstChild();  child != null;
					child = child.getNextSibling()) {
				debugElement((Element)child, depth + 1);
			}
			for (int i = 0;  i < depth;  ++i) {
				System.out.print("\t");
			}
			System.out.printf("</%s>\n", element.getTagName());
		} else {
			System.out.println("/>");
		}
	}
}

class Value {

	long value;

	public Value(String s) throws Exception {

		if (s.matches("[+-]?[0-9]+")) {
			value = Long.parseLong(s);
		} else if (s.matches("0[Xx][0-9A-Fa-f]+")) {
			value = Long.parseLong(s.substring(2), 16);
		} else if (s.matches("%n\\([CDEFGAB][+-]*[0-9]+\\)")) {
			value = analyzeNote(s.substring(3, s.length() - 1));
		} else if (s.matches("%(not|neg|and|or|xor|add|sub|mul|div|mod|shl|shr|bits)\\(.+\\)")) {
			value = analyzeExpression(s);
		} else {
			throw new Exception("Invalid value: " + s);
		}
	}

	public long getValue() {
		return value;
	}

	long analyzeNote(String s) {

		String sNote = s.substring(0, 1);
		long increment = 0;
		long octave = 0;
		for (int i = 1;  i < s.length();  ++i) {
			switch (s.charAt(i)) {
			case '+':
				++increment;
				continue;
			case '-':
				--increment;
				continue;
			default:
				octave = Long.parseLong(s.substring(i));
				break;
			}
			break;
		}
		/* %n(C-1) == 0 */
		return (octave + 1) * 12 + "C D EF G A B".indexOf(sNote) + increment;
	}

	long analyzeExpression(String s) throws Exception {

		if (s.matches("%not\\(.+\\)")) {
			return ~analyzeExpression(s.substring(5, s.length() - 1));
		} else if (s.matches("%neg\\(.+\\)")) {
			return -analyzeExpression(s.substring(5, s.length() - 1));
		} else if (s.matches("%(and|or|xor|add|sub|mul|div|mod|shl|shr)\\(.+\\)")) {
			String[] sExpressions = new String[2];
			for (int i = 0;  i < s.length();  ++i) {
				if (s.charAt(i) != ',') {
					continue;
				}
				try {
					if (s.indexOf("or") == 1) {
						sExpressions[0] = s.substring(4, i);
					} else {
						sExpressions[0] = s.substring(5, i);
					}
					sExpressions[1] = s.substring(i + 1, s.length() - 1);
					long[] values = analyzeExpressionList(sExpressions);
					if (s.indexOf("and") == 1) {
						return values[0] & values[1];
					} else if (s.indexOf("or") == 1) {
						return values[0] | values[1];
					} else if (s.indexOf("xor") == 1) {
						return values[0] ^ values[1];
					} else if (s.indexOf("add") == 1) {
						return values[0] + values[1];
					} else if (s.indexOf("sub") == 1) {
						return values[0] - values[1];
					} else if (s.indexOf("mul") == 1) {
						return values[0] * values[1];
					} else if (s.indexOf("div") == 1) {
						return values[0] / values[1];
					} else if (s.indexOf("mod") == 1) {
						return values[0] % values[1];
					} else if (s.indexOf("shl") == 1) {
						return values[0] << values[1];
					} else if (s.indexOf("shr") == 1) {
						return values[0] >> values[1];
					}
				} catch (Exception e) {
					continue;
				}
			}
			throw new Exception("Invalid value: " + s);
		} else if (s.matches("%bits\\(.+\\)")) {
			String[] sExpressions = new String[3];
			for (int i = 0;  i < s.length();  ++i) {
				if (s.charAt(i) != ',') {
					continue;
				}
				for (int j = i + 1;  j < s.length();  ++j) {
					try {
						sExpressions[0] = s.substring(6, i);
						sExpressions[1] = s.substring(i + 1, j);
						sExpressions[2] = s.substring(j + 1, s.length() - 1);
						long[] values = analyzeExpressionList(sExpressions);
						if (values[1] < values[2]) {
							long tmp = values[1];
							values[1] = values[2];
							values[2] = tmp;
						}
						values[0] >>= values[2];
						values[1] -= values[2];
						long mask = 1;
						for (;  values[1] > 0;  --values[1]) {
							mask = (mask << 1) | 1;
						}
						return values[0] & mask;
					} catch (Exception e) {
						continue;
					}
				}
			}
			throw new Exception("Invalid value: " + s);
		} else {
			return (new Value(s)).getValue();
		}
	}

	long[] analyzeExpressionList(String[] sExpressions) throws Exception {

		long[] result = new long[sExpressions.length];
		for (int i = 0;  i < result.length;  ++i) {
			result[i] = (new Value(sExpressions[i])).getValue();
		}
		return result;
	}
}


class ValueList {

	long[] values;

	public ValueList(String s) throws Exception {

		String[] sValues = s.split("\\s");
		values = new long[sValues.length];
		for (int i = 0;  i < sValues.length;  ++i) {
			values[i] = (new Value(sValues[i])).getValue();
		}
	}

	public ValueList(String s, int channel) throws Exception {

		String[] sValues = s.split("\\s");
		values = new long[sValues.length];
		for (int i = 0;  i < sValues.length;  ++i) {
			values[i] = (new Value(
					sValues[i].replaceAll("%ch", Integer.toString(channel))))
					.getValue();
		}
	}

	public ValueList(String s, int channel, int csIndex) throws Exception {

		String[] sValues = s.split("\\s");
		int cs = 0;
		values = new long[sValues.length];
		for (int i = 0;  i < sValues.length;  ++i) {
			if (sValues[i].equals("%cs")) {
				values[i] = 128 - (cs & 127);
			} else {
				values[i] = (new Value(
						sValues[i].replaceAll("%ch",
						Integer.toString(channel))))
						.getValue();
			}
			if (i >= csIndex) {
				cs += values[i];
			}
		}
	}

	public long[] getValues() {
		return values;
	}
}


class MultiDeviceMidiPlayer {

	ArrayList<MidiDevice> deviceList;

	int maxTracks;
	Receiver[] receivers;
	MidiMessage[][] messagess;
	long[][] tickss;
	int[] eventIndexes;
	long maxTick;
	long tickGcd;

	int maxSetTempos;
	long[] setTempoTicks;
	long[] setTempoUspgcds;
	int setTempoIndex;

	ScheduledExecutor scheduledExecutor;
	ScheduledCancellable scheduledCancellable;
	long tick;


	public MultiDeviceMidiPlayer(
			String[] sDeviceNames,
			Track[] tracks,
			int tpqn)
			throws Exception {

		try {
			// get receivers

			Hashtable<String, MidiDevice.Info> deviceInfoHash
					= getDeviceInfos();
			deviceList = new ArrayList<MidiDevice>();
			receivers = new Receiver[sDeviceNames.length];
			for (int i = 0;  i < sDeviceNames.length;  ++i) {
				MidiDevice.Info info = deviceInfoHash.get(sDeviceNames[i]);
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (!device.isOpen()) {
					device.open();
					deviceList.add(device);
				}
				receivers[i] = device.getReceiver();
			}

			// get set-tempo information

			TreeMap<Long, Integer> uspqnMap = new TreeMap<Long, Integer>();
			uspqnMap.put(0L, 60 * 1000000 / 120);
					// 120 BPM
					// = 120 QN/min
					// = 120/60 QN/sec
					// = 120/60/1000000 QN/usec
					// 60 * 1000000 / 120 usec/QN
			maxTracks = tracks.length;
			messagess = new MidiMessage[maxTracks][];
			tickss = new long[maxTracks][];
			eventIndexes = new int[maxTracks];
			maxTick = 0;
			tickGcd = 0;
			for (int i = 0;  i < maxTracks;  ++i) {
				Track track = tracks[i];
				messagess[i] = new MidiMessage[track.size()];
				tickss[i] = new long[track.size()];
				for (int j = 0;  j < track.size();  ++j) {
					MidiEvent event = track.get(j);
					MidiMessage message = messagess[i][j] = event.getMessage();
					long tick = tickss[i][j] = event.getTick();
					tickGcd = gcd(tick, tickGcd);
					if (message instanceof MetaMessage
							&& ((MetaMessage)message).getType() == 0x51) {
						// Set Tempo
						byte[] data = ((MetaMessage)message).getData();
						int uspqn = ((data[0] & 0xff) << 16)
								| ((data[1] & 0xff) << 8)
								| (data[2] & 0xff);
						uspqnMap.put(tick, uspqn);
					}
				}
				eventIndexes[i] = 0;
				maxTick = Math.max(track.ticks(), maxTick);
			}

			maxSetTempos = uspqnMap.size();
			setTempoTicks = new long[maxSetTempos + 1];
			setTempoUspgcds = new long[maxSetTempos];
			setTempoIndex = 0;
			Iterator iter = uspqnMap.keySet().iterator();
			while (iter.hasNext()) {
				long tick = (Long)iter.next();
				setTempoTicks[setTempoIndex] = tick;
				int uspqn = uspqnMap.get(tick);
				setTempoUspgcds[setTempoIndex++] = tickGcd * uspqn / tpqn;
						// uspqn usec/QN
						// = uspqn/tpqn usec/tick
						// = gcd*uspqn/tpqn usec/gcd
			}
			setTempoTicks[maxSetTempos] = -1;
			setTempoIndex = 0;
			tick = 0 - tickGcd;

			scheduledExecutor = new ScheduledExecutor(100);
			scheduledCancellable = scheduledExecutor.scheduleAtFixedRate(
					new TickTask(),
					setTempoUspgcds[0],
					setTempoUspgcds[0],
					TimeUnit.MICROSECONDS);

			while (scheduledCancellable != null) {
				try {
					Thread.sleep(500);
				} catch (Exception e) {
				}
			}

		} finally {
			// close devices

			for (int i = 0;  i < deviceList.size();  ++i) {
				deviceList.get(i).close();
			}
		}
	}

	static Hashtable<String, MidiDevice.Info> getDeviceInfos() {

		Hashtable<String, MidiDevice.Info> deviceInfoHash
				= new Hashtable<String, MidiDevice.Info>();
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0;  i < infos.length;  ++i) {
			MidiDevice device = null;
			try {
				device = MidiSystem.getMidiDevice(infos[i]);
				device.open();
			} catch (Exception e) {
				continue;
			}
			try {
				Receiver receiver = null;
				try {
					receiver = device.getReceiver();
					if (receiver == null) {
						continue;
					}
					receiver.close();
				} catch (Exception e) {
					continue;
				}
			} finally {
				device.close();
			}
			deviceInfoHash.put(infos[i].getName(), infos[i]);
		}
		return deviceInfoHash;
	}

	static long gcd(long a, long b) {

		if (b == 0) {
			return a;
		}
		if (a % b == 0) {
			return b;
		}
		return gcd(b, a % b);
	}

	class TickTask implements Runnable {

		public synchronized void run() {

			long tick0 = tick += tickGcd;
			if (tick0 > maxTick) {
				scheduledCancellable.cancel(false);
				scheduledCancellable = null;
				return;
			}
			for (int i = 0;  i < maxTracks;  ++i) {
				MidiMessage[] messages = messagess[i];
				long[] ticks = tickss[i];
				Receiver receiver = receivers[i];
				int maxEvents = ticks.length;
				int eventIndex = eventIndexes[i];
				for (;  eventIndex < maxEvents && ticks[eventIndex] <= tick0;
						++eventIndex) {
					MidiMessage message = messages[eventIndex];
					if (message instanceof ShortMessage
							|| message instanceof SysexMessage) {
						receiver.send(message, -1);
					}
				}
				eventIndexes[i] = eventIndex;
			}
			if (tick0 == setTempoTicks[setTempoIndex]) {
				while (!scheduledCancellable.cancel(false)) {
				}
				while (!scheduledCancellable.isDone()) {
				}
				scheduledCancellable = scheduledExecutor.scheduleAtFixedRate(
						this,
						setTempoUspgcds[setTempoIndex],
						setTempoUspgcds[setTempoIndex],
						TimeUnit.MICROSECONDS);
				++setTempoIndex;
			}
		}
	}
}


