# Unofficial fork of Traccar Client for Android

This fork should have all features as origin. There are some more features to work as required.

## Features

- different intervals for 'on battery' and 'charging' state
- ability to checking 'distance' and 'angle' only while charging
- option to report ignition state based on charging state
- use real POST request with POST-style data

## Todo
- check accuracy of 'accuracy' - there is always 0 on Android 4.3 with GPS only selected
- temperature reporting - battery / ambient temperature - deviceTemp / temp0 / temp1
- use accelerometer to detect motion and update GPS (is this more power efficent? is this needed when using different interval for 'charging' state?)

## Not implemented features

- gzip compression of data (longer than 150 chars) - server doesn't support gzip
- HTTP headers optimization (remove useless headers) - HttpURLConnection doesn't support it

### Author of original application

- Anton Tananaev

### License

    Apache License, Version 2.0

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
