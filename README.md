# Google Play Cancellation Response Scraper

This is an automated workflow for fetching cancellation survey responses for Google Play Store subscriptions, and posting them to Slack.

Built on [GitHub Actions](https://docs.github.com/en/actions), [Docker](https://docs.docker.com/get-started/), and [Kotlin Scripting](https://kotlinlang.org/docs/custom-script-deps-tutorial.html).

## Example Slack Output

<img src="https://github.com/user-attachments/assets/33951b92-8860-4aa9-972a-710cdf636d0b" width="500">

## How to Use

1. Create a fork of this repo by clicking "Use this template" in the upper-right hand corner of this page.

2. Define the following variables as GitHub Actions secrets:
  * **`GCS_BUCKET_ID`** - The Google Cloud Storage bucket ID that your cancellation survey responses live in.<br>**Example:** `pubsite_prod_1234567890123456789`. This can be obtained by:
    * On the "Subscription cancellations" page of the Google Play Console, click the "Download written responses" button:<br><img src="https://github.com/user-attachments/assets/52ad3487-ebff-49f8-b677-0defd70d83b0" width="400">
    * Go to your browser's download history and copy the download link for the file.
    * Near the beginning of the URL, look for a path segment that begins with `pubsite_prod`.  This is your bucket id.
  * **`SERVICE_ACCOUNT_JSON`** - Base64 encoded JSON key for a Google Cloud service account. After downloading the JSON key, you can base64 encode it with this command: `base64 -w0 /path/to/key.json`
    * This account must have the `Storage Object Viewer` role assigned to it in your Google Cloud project.
    * This account must also be added as a user to the Google Play Console, and must be given the following permissions:
      * View app information and download bulk reports (read-only)
      * View financial data, orders, and cancellation survey responses
  * **`SLACK_API_TOKEN`** - [Bot token](https://api.slack.com/concepts/token-types#bot) for the Slack app to be used for posting messages (starts with `xoxb-`).
    * This Slack app must be given the [`chat.write`](https://api.slack.com/scopes/chat:write) scope in order to post messages. 

3. Edit the mapping files in the `config` directory of the repo.  Entries are keys/values separated by colons `:`, and each file can have multiple entries (one per line).
  * **`package-map.txt`** - Map of Android application IDs (package names) to user-visible app names.<br>**Example:** `com.example.app:Example App Name`.
  * **`sku-map.txt`** - Map of subscription IDs (product IDs) for your apps' Google Play subscriptions.<br>**Example:** `example-sku-id:Example Subscription Name`.
  * **`slack-channel-map.txt`** - Map of Android application IDs to the Slack channels that responses will be posted to. Include the `#` prefix in the channel name.<br>**Example:** `com.example.app:#example-app-cancellation-responses`.

4. Ensure that the "Build and cache image" action has been run at least once after the mapping files have been edited.  By default, this is done after each push to main.

5. Use the `Run scraper` action to fetch and post new responses to Slack.  Or, configure workflow triggers inside `run-scraper.yml` as desired.

## Notes

An `output` branch is automatically created in the repo as part of the workflow.  This branch stores the downloaded CSVs as well as SQLite databases of the response data which are used.  To reset this data and start afresh, simply delete the `output` branch.

This project utilizes GitHub Actions caches to store the generated Docker image, as well as the individual layers in order to speed up container build times.

For more info on downloading cancellation survey responses from the Play Console, see [this help page](https://support.google.com/googleplay/android-developer/answer/6135870).

----

Check out some of the light-amplifying stories that our community is bringing to life at **Angel Studios**!<br>Start watching our fan-curated movies and TV shows for free: https://www.angel.com
