#!/bin/bash
set -e
ant -f build_assign_speech.xml
java -cp assign_speech.jar:assign_speech-submit.jar -server -mx2000m edu.berkeley.nlp.assignments.assignspeech.student.SpeechRecognizerFactory
