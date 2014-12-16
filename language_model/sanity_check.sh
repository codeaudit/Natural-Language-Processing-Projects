#!/bin/sh
set -e
sudo ant -f build_assign1.xml
java -cp assign1.jar:assign1-submit.jar -ea -server -mx50m edu.berkeley.nlp.assignments.assign1.LanguageModelTester -path assign1_data -lmType TRIGRAM -sanityCheck
