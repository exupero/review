(ns review.xf)

(defn split-by [pred]
  (fn [rf]
    (let [b (volatile! nil)]
      (fn
        ([] (rf))
        ([res] (rf (rf res @b)))
        ([res item]
         (if-let [b' @b]
           (if (pred item)
             (do (vreset! b [item]) (rf res b'))
             (do (vswap! b conj item) res))
           (do (vreset! b [item]) res)))))))

