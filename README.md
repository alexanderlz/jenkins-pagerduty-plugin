# Jenkins PagerDuty Plugin

This plugin provides two post-build actions/pipeline steps for interacting with the [PagerDuty](https://www.pagerduty.com/) platform.

## Trigger/Resolve Incidents

Using the "PagerDuty Incident Trigger" action, you can trigger or resolve incidents based on the results of a job.

## Create Change Events

Using the "PagerDuty Change Events" action, you can create a [change event](https://support.pagerduty.com/docs/change-events) when a job completes.

## Getting Started

Before you can use this plugin you'll need an integration key (also known as a routing key). This key tells PagerDuty which service the incoming event should be sent to. If you already have a service in PagerDuty you can [add an integration to it](https://support.pagerduty.com/docs/services-and-integrations#add-integrations-to-an-existing-service); otherwise, you can add the integration when [creating a new service](https://support.pagerduty.com/docs/services-and-integrations#create-a-new-service). For the integration type, choose "Jenkins CI".

Once you have an integration key you can add the appropriate action to your Jenkins project and supply it with the integration key. Be sure to select which job results you want to send an event to PagerDuty.

[![Gitter](https://badges.gitter.im/jenkinsci/pagerduty-plugin.svg)](https://gitter.im/jenkinsci/pagerduty-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
