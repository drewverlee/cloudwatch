(ns aws.cloudwatch
  (:require
   [cognitect.aws.client.api :as aws]))

(defn lazy-concat
  [colls]
  (lazy-seq
   (when-first [c colls]
     (lazy-cat c (lazy-concat (rest colls))))))

(comment

  (def ^:private log-group-name
    "TODO fill in")

  (def cloudwatch-client (aws/client {:api :logs :credentials-provider "TODO" :region "TODO"}))

  (def log-group-info {:logGroupName log-group-name :cloudwatch-client cloudwatch-client})

  nil)

(defn- logGroupName->DescribeLogStream! [{:keys [cloudwatch-client logGroupName token]}]
  (println "logGroupName->DescribeLogStream!")
  (aws/invoke cloudwatch-client {:op :DescribeLogStreams
                                 :request (cond-> {:logGroupName logGroupName
                                                   :orderBy "LastEventTime"
                                                   :limit 50
                                                   :descending true} token (assoc :nextToken token))}))

(defn logGroupName->DescribeLogStreamsLazyList!
  [logGroupName]
  (lazy-concat
   (iteration
    (fn [token]
      (logGroupName->DescribeLogStream! (assoc logGroupName :token token)))
    :vf :logStreams
    :kf :nextToken)))

(comment

  (def DescribeLogStreamsLazyList! (logGroupName->DescribeLogStreamsLazyList! log-group-info))

  (take 1 DescribeLogStreamsLazyList!)

  nil)

(defn DescribeLogStreams->GetLogEvents!
  [{:keys [cloudwatch-client] :as DescribeLogStreams}]
  (println "DescribeLogStreams->GetLogEvents!:")
  (aws/invoke cloudwatch-client {:op :GetLogEvents
                                 :request (dissoc DescribeLogStreams :cloudwatch-client)}))

(comment

  (def DescribeLogStreams (first DescribeLogStreamsLazyList!))

  (def GetLogEvents
    (-> DescribeLogStreamsLazyList!
        first
        (merge log-group-info)
        DescribeLogStreams->GetLogEvents!))
  nil)

(defn DescribeLogStreams->GetLogEventsLazyList!
  [DescribeLogStreams]
  (lazy-concat
   (iteration
    (fn [token]
      (DescribeLogStreams->GetLogEvents! (assoc DescribeLogStreams :token token)))
    :vf :events
    :kf :nextToken)))

(defn DescribeLogStreamsLazyList->GetLogEventsLazyList!
  #_[{[DescribeLogStreams & DescribeLogStreamsLazyList] :DesribeLogStreamsLazyList :as m}]
  [{:keys [DescribeLogStreamsLazyList] :as m}]
  (println "D")
  (when-let [dlsl (first DescribeLogStreamsLazyList)]
    (concat
     (DescribeLogStreams->GetLogEventsLazyList! (merge dlsl  (dissoc m :DesribeLogStreamsLazyList)))
     (DescribeLogStreamsLazyList->GetLogEventsLazyList! (assoc m :DesribeLogStreamsLazyList (rest DescribeLogStreamsLazyList))))))

(comment

  ;; This results in a Stack overflow with lots of "D"'s being printed because  DescribeLogStreamsLazyList->GetLogEventsLazyList!
  ;; is called over and over
  (take 1
        (DescribeLogStreamsLazyList->GetLogEventsLazyList! (merge log-group-info {:DescribeLogStreamsLazyList DescribeLogStreamsLazyList!})))

  nil)
