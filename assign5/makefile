
TYPE=MODEL1
TRAIN=10000

FLAGS=-cp assign_align.jar:assign_align-submit.jar -server edu.berkeley.nlp.assignments.align.AlignmentTester -path align_data

.PHONY: assign_align-submit.jar
assign_align-submit.jar:
	ant -f build_assign_align.xml

pull:
	git reset --hard HEAD~1
	git pull

sanity: assign_align-submit.jar
	java -ea -mx50m $(FLAGS) -alignerType $(TYPE) -sanityCheck -maxTrain 0

full_local: assign_align-submit.jar
	java -ea -Xmx8g $(FLAGS) -alignerType $(TYPE) -maxTrain $(TRAIN) -data test -noprint

real_local: assign_align-submit.jar
	java -Xmx8g $(FLAGS) -alignerType $(TYPE) -maxTrain 10000 -data test -noprint

full:
	ssh i.anting.me 'cd ~/288/assign5 && make pull && make full_local'

update:
	git add .
	git commit --amend --no-edit
	git push -f
	make full
