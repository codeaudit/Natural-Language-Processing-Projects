#!/bin/bash
set -e
ant -f build_assign_speech.xml
java -cp assign_speech.jar:assign_speech-submit.jar -server -ea -mx300m edu.berkeley.nlp.assignments.assignspeech.SpeechTester -path csr-288 -sanityCheck
