      "sender": "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK",
      "recipient": "FLx88wdsbLQyZRmbqtpeXA9u5FG9EyCash",

```json
{
  "query": {
    "terms": {
      "fields": [
        "sender",
        "recipient"
      ],
      "values": [
        "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK"
      ]
    }
  },
  "filter": {
    "terms": {
      "fields": [
        "sender",
        "recipient"
      ],
      "values": [
        "FLx88wdsbLQyZRmbqtpeXA9u5FG9EyCash"
      ]
    }
  }
}
```