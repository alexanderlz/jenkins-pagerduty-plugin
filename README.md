# jenkins-pagerduty-plugin
Jenkins plugin that allows triggering [PagerDuty] (https://www.pagerduty.com/) incidents as postbuild


PagerDuty API calls done through this great [utility by square] (https://github.com/square/pagerduty-incidents)


Usage:

```
$ git clone git@github.com:alexanderlz/jenkins-pagerduty-plugin.git
$ cd jenkins-pagerduty-plugin
$ mvn clean package
```

then upload the hpi file to jenkins

