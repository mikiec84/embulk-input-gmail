# Gmail input plugin for Embulk

 Gmail API の検索結果を取得します。

## Overview

* **Plugin type**: input
* **Resume supported**: no
* **Cleanup supported**: no
* **Guess supported**: no

## Configuration

- **client_secret**: client secret json file of Google APIs. (string, required)
- **tokens_directory**: tokens directory of Gmail API Client Library for Java. (string, required)
- **user**: search user. (string, default: `me`)
- **query**: search query. (string, default: ``(empty string))
- **after_than**: Gmail search query "after_than: xxx".
                  Concat this config string, after "query" config string.
                  You use if '-o' option. (string, default: null)

## Example

### basic

```yaml
in:
  type: gmail
  client_secret: ./client_secret_xxx.json
  tokens_directory: ./tokens
  query: "\"Google アラート\""
  columns:
    - {name: Subject, type: string}
    - {name: Body, type: string}
```

Sending query is `"Google アラート"`.

### Use `-o` option.

```yaml
in:
  type: gmail
  client_secret: ./client_secret_xxx.json
  tokens_directory: ./tokens
  query: "\"Google アラート\""
  after_than: 2018/10/1 # automatically update to last query sending time.
  columns:
    - {name: Subject, type: string}
    - {name: Body, type: string}
```

Sending query is `"Google アラート" after_than:2018/10/1`.

## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
