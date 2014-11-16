
TYPE=HEURISTIC
TRAIN=10000

FLAGS=-cp assign_align.jar:assign_align-submit.jar -server edu.berkeley.nlp.assignments.align.AlignmentTester -path align_data

.PHONY: assign_align-submit.jar
assign_align-submit.jar:
	ant -f build_assign_align.xml

pull:
	git reset --hard HEAD~1
	git pull

sanity: assign_align-submit.jar
	java $(FLAGS) -ea -mx50m -alignerType $(TYPE) -sanityCheck -maxTrain 0

full_local: assign_align-submit.jar
	java $(FLAGS) -ea -Xmx8g -alignerType $(TYPE) -maxTrain $(TRAIN) -data test

real_local: assign_align-submit.jar
	java $(FLAGS) -Xmx8g -alignerType $(TYPE) -maxTrain 10000 -data test

full:
	ssh i.anting.me 'cd ~/288/assign5 && make pull && make full_local'
