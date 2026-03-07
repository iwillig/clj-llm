set positional-arguments

poc *args:
    clojure -M -m llm.main {{args}}

test:
    clojure -M:dev:test

nrepl:
    clojure -M:nrepl

jar:
    clojure -T:build uber

native:
    clojure -T:build native

native-smoke:
    ./target/clj-llm prompt --no-stream "Say hi briefly."
