import matplotlib.pyplot as plt

f = file("firstresult.out", "r")

threshes = []
f1s = []
times = []

for i in range(20):
	index = int(f.readline())
	f1 = float(f.readline())
	time = float(f.readline())

	thresh = 5 + index * 0.25
	time = int(time/1000)

	threshes.append(thresh)
	f1s.append(f1)
	times.append(time)

plt.plot(threshes, times, "bo", threshes, times, "k")
plt.ylabel("Compute Time (seconds)")
plt.xlabel("Prune Threshold (negative log prob)")
plt.show()
