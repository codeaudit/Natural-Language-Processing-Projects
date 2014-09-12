#!/bin/bash
set -e
sudo ant -f build_assign1.xml
java -cp assign1.jar:assign1-submit.jar -server -ea -mx2000m edu.berkeley.nlp.assignments.assign1.LanguageModelTester -path assign1_data -noprint -lmType TRIGRAM
