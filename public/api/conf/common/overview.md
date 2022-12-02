This CTC Guarantee Balance API will allow you to provide your users with up to date information from HMRC about their Guarantee Balance or balances. This helps your users plan ahead when organising goods transit movements into or out of the UK by telling your users how much of their Guarantee Balance they have left to use.

This CTC Guarantee Balance API works alongside the [CTC Traders API](https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/common-transit-convention-traders/1.0) which allows your software to send departure and arrival movement notifications to the New Computerised Transit System (NCTS).

You must note:

The Guarantee Balance API does not provide real time data on your users balance. It is possible that even a very short delay could coincide with events that could change their balance information. The balance responses could become out of date quickly if your users have a lot of transit movements progressing at the same time.

We have released the Guarantee Balance API to HMRC's sandbox environment. You can now run tests once you have finished your software development.

You should read:

* CTC Guarantee Balance Service Guide for how to integrate with this API
* Step by step Guide to Testing to learn how to test your software and ensure it is compatible with our CTC Guarantee Balance API

Further details about the User Restricted Authentication are given on the [Authorisation](https://developer.service.hmrc.gov.uk/api-documentation/docs/authorisation) page.

For more information about how to develop your own client applications, including example clients for this API, see [Tutorials](https://developer.service.hmrc.gov.uk/api-documentation/docs/tutorials).
