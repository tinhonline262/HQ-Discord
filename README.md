![](trivia.png)

![](words.png)

# HQ-Discord
Just scrapes the HQ api and prints the question/puzzle data out to a discord channel

This is still a work in progress. Code style is kinda awful, this is mostly an exercise in teaching
myself about interacting with http clients and websockets, with a little reactive programming thrown in for good measure.

DISCLAIMER
==========

This program is for educational purposes only. Use of this program breaks HQs Terms of Use, which state:

c. You shall not: (i) take any action that imposes or may impose (as determined by us in our sole discretion) an unreasonable or disproportionately large load on our (or our third party providers’) infrastructure; (ii) interfere or attempt to interfere with the proper working of the Services or any activities conducted on the Services; (iii) bypass, circumvent or attempt to bypass or circumvent any measures we may use to prevent or restrict access to the Services (or other accounts, computer systems or networks connected to the Services); (iv) run any form of auto-responder or "spam" on the Services; (v) use manual or automated software, devices, or other processes to “crawl” or “spider” any page of the App without our express written permission; **(vi) harvest or scrape any Content from the Services;** or (vii) otherwise take any action in violation of our guidelines and policies.

Again, this is written purely for me to learn about how to mess with various things I'd never had occasion to use before. Feel free to fork/modify/use this code however.

HOW TO IMPLEMENT
================

You need the following:

An HQ bearer token
<br>A Discord Bot token

Copy/paste the two tokens into their respective spots near the top of BotMain.java.
Then in main() copy paste your channel ID into its appropriate spot. If you would like, you can hardcode channel id's into the appropriate place in main() to retain them every time you start the bot.

BOT COMMANDS
============

.check - checks to see if the bot is currently working

.checkGames - checks to see what games the current channel will receive messages about

.addTrivia - subscribes the current channel to HQ trivia messages

.addWords - subscribes the current channel to HQ words messages

.removeTrivia - unsubscribes the current channel from trivia messages

.removeWords - unsubscribes the current channel from words messages

.start - this command is what tells the bot to start listening for HQ broadcasts. Once called, this command will be unusable as the 
         hq listener will keep running indefinitely, until the bot software is closed or the bot crashes. This bot will not output any            HQ Trivia or Words output until this command is called for the first time. 
