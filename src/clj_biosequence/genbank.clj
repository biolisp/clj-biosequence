(ns clj-biosequence.genbank
  (:require [clojure.data.xml :refer [parse]]
            [clojure.data.zip.xml :as zf]
            [clojure.zip :refer [xml-zip node]]
            [clojure.string :refer [split]]
            [clojure.java.io :refer [reader]]
            [fs.core :refer [file?]]
            [clj-biosequence.core :as bs]
            [clj-biosequence.eutilities :refer [e-search e-fetch]]
            [clj-biosequence.alphabet :refer [alphabet-is-protein?]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; genbank citation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankCitation [src])

(extend genbankCitation
  bs/biosequenceCitation
  (assoc bs/default-biosequence-citation
    :title
    (fn [this]
           (zf/xml1-> (xml-zip (:src this))
                      :GBReference_title zf/text))
    :journal
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_journal zf/text))
    :year
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_journal zf/text))
    :volume
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_journal zf/text))
    :pstart
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_journal zf/text))
    :pend
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_journal zf/text))
    :authors
    (fn [this]
      (zf/xml-> (xml-zip (:src this))
                :GBReference_authors
                :GBAuthor zf/text))
    :pubmed
    (fn [this]
      (zf/xml1-> (xml-zip (:src this))
                 :GBReference_pubmed zf/text))
    :crossrefs
    (fn [this]
      (map (fn [x]
             (assoc {}
               (zf/xml1-> x :GBXref :GBXref_dbname
                          zf/text)
               (zf/xml1-> x :GBXref :GBXref_id
                          zf/text)))
           (zf/xml-> (xml-zip (:src this))
                     :GBReference_xref))))
  bs/biosequenceNotes
  (assoc bs/default-biosequence-notes
    :notes
    (fn [this]
      (zf/xml-> (xml-zip (:src this))
                :GBReference_remark zf/text))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; interval
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankInterval [src])

(extend genbankInterval
  bs/biosequenceID
  (assoc bs/default-biosequence-id
    :accession (fn [this]
                 (bs/get-text this :GBInterval_accession)))
  bs/biosequenceInterval
  (assoc bs/default-biosequence-interval
    :start
    (fn [this]
      (let [r (or (bs/get-text this :GBInterval_from)
                  (bs/get-text this :GBInterval_point))]
        (Integer/parseInt r)))
    :end
    (fn [this]
      (if-let [r (or (bs/get-text this :GBInterval_to)
                  (bs/get-text this :GBInterval_point))]
        (Integer/parseInt r)))
    :point
    (fn [this]
      (if-let [r (bs/get-text this :GBInterval_point)]
        (Integer/parseInt r)))
    :comp?
    (fn [this]
      (let [c (bs/get-one this :GBInterval_iscomp (zf/attr :value))]
        (if (= "true" c) true false))))
  bs/biosequenceTranslation
  (assoc bs/default-biosequence-translation
    :frame
    (fn [this]
      (:frame this))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; qualifier
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankQualifier [src])

(extend genbankQualifier
  bs/biosequenceNameObject
  (assoc bs/default-biosequence-nameobject
    :obj-type
    (fn [this]
      (bs/get-text this :GBQualifier_name))
    :obj-value
    (fn [this]
      (bs/get-text this :GBQualifier_value))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; dbref
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankDbRef [src])

(extend genbankDbRef
  bs/biosequenceDbRef
  (assoc bs/default-biosequence-dbref
    :database-name
    (fn [this]
      (first
       (split (bs/get-text this :GBQualifier_value) #":")))
    :object-id
    (fn [this]
      (second
       (split (bs/get-text this :GBQualifier_value) #":")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; feature
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn qualifiers
  "Collection of qualifiers in a genbankFeature record."
  [feature]
  (->> (bs/get-list feature :GBFeature_quals :GBQualifier)
       (map #(->genbankQualifier (node %)))))

(defn filter-qualifiers
  "Filter qualifiers in a genbankFeature record using obj-type
  function."
  [feature qname]
  (filter #(= (bs/obj-type %) qname)
          (qualifiers feature)))

(defn get-qualifiers
  "Returns qualifier values from a genbankFeature record that have a
  particular bs-name."
  [feature name]
  (let [q (filter-qualifiers feature name)]
    (if (seq q)
      (map bs/obj-value q))))

(defn feature-location
  "Returns the value corresponding to the 'GBFeature_location' element
  of a genbank feature element."
  [feature]
  (bs/get-text feature :GBFeature_location))

(defrecord genbankFeature [src])

(extend genbankFeature
  bs/biosequenceFeature
  (assoc bs/default-biosequence-feature
    :operator (fn [this]
                (bs/get-text this :GBFeature_operator)))
  bs/biosequenceGene
  (assoc bs/default-biosequence-gene
    :locus-tag (fn [this] (get-qualifiers this "locus_tag"))
    :products (fn [this] (get-qualifiers this "product")))
  bs/biosequenceID
  (assoc bs/default-biosequence-id
    :accessions
    (fn [this]
      (map bs/accession (bs/intervals this))))
  bs/biosequenceProtein
  (assoc bs/default-biosequence-protein
    :calc-mol-wt (fn [this]
                   (get-qualifiers this "calculated_mol_wt")))
  bs/biosequenceEvidence
  (assoc bs/default-biosequence-evidence
    :evidence
    (fn [this]
      (->> (filter #(re-find #"evidence" (bs/obj-type %))
                   (qualifiers this))
           (map #(str (bs/obj-type %) ":" (bs/obj-value %))))))
  bs/biosequenceNotes
  (assoc bs/default-biosequence-notes
    :notes (fn [this] (get-qualifiers this "note")))
  bs/biosequenceTranslation
  (assoc bs/default-biosequence-translation
    :trans-table (fn [this]
                   (get-qualifiers this "transl_table"))
    :codon-start (fn [this]
                   (get-qualifiers this "codon_start"))
    :translation (fn [this]
                   (get-qualifiers this "translation")))
  bs/biosequenceNameObject
  (assoc bs/default-biosequence-nameobject
    :obj-type (fn [this]
                (bs/get-text this :GBFeature_key)))
  bs/biosequenceIntervals
  (assoc bs/default-biosequence-intervals
    :intervals
    (fn [this]
      (let [m (atom 1)
            r {1 3 2 2 0 1}
            f {3 2 2 1 1 0}]
        (map #(let [i (->genbankInterval (node %))
                    fi (assoc i :frame
                              (if (bs/comp? i) (* @m -1) @m))
                    l (if (bs/comp? fi)
                        (- (+ (- (bs/start fi) (bs/end fi)) 1)
                           (get f @m))
                        (- (+ (- (bs/end fi) (bs/start fi)) 1)
                           (get f @m)))]
                (reset! m (get r (mod l 3)))
                fi)
             (bs/get-list this :GBFeature_intervals :GBInterval)))))
  bs/biosequenceDbRefs
  (assoc bs/default-biosequence-dbrefs
    :get-db-refs
    (fn [this]
      (->> (qualifiers this)
           (filter #(= (bs/obj-type %) "db_xref"))
           (map #(->genbankDbRef (:src %)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; sequence
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankTaxRef [src])

(extend genbankTaxRef
  bs/biosequenceTaxonomy
  (assoc bs/default-biosequence-tax
    :lineage (fn [this] (bs/get-text this :GBSeq_taxonomy))
    :tax-name (fn [this] (bs/get-text this :GBSeq_organism)))
  bs/biosequenceFeatures
  (assoc bs/default-biosequence-features
    :feature-seq
    (fn [this]
      (map #(->genbankFeature %)
           (:content (some #(if (= (:tag %) :GBSeq_feature-table)
                              %)
                           (:content (:src this))))))
    :filter-features
    (fn [this name]
      (filter #(= (bs/obj-type %) name)
              (bs/feature-seq this))))
  bs/biosequenceDbRefs
  (assoc bs/default-biosequence-dbrefs
    :get-db-refs
    (fn [this]
      (->> (bs/filter-features this "source")
           (mapcat #(bs/get-db-refs %))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; sequence
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- gb-date
  [str]
  (bs/make-date str (bs/make-date-format "dd-MMM-yyyy")))

(defn gb-seq-comment
  "Returns strings from GbSeq_comment xml elements."
  [gbseq]
  (fn [this]
    (bs/get-list this :GBSeq_comment zf/text)))

(defrecord genbankSequence [src alphabet])

(extend genbankSequence
  bs/biosequenceGene
  (assoc bs/default-biosequence-gene
    :locus (fn [this] (bs/get-text this :GBSeq_locus)))
  bs/biosequenceID
  (assoc bs/default-biosequence-id
    :accession
    (fn [this] (bs/get-text this :GBSeq_primary-accession))
    :accessions
    (fn [this]
      (concat
       (bs/get-list this :GBSeq_secondary-accessions
                    :GBSecondary-accn zf/text)
       (bs/get-list this :GBSeq_other-seqids :GBSeqid zf/text)))
    :creation-date
    (fn [this]
      (gb-date (bs/get-text this :GBSeq_create-date)))
    :update-date
    (fn [this]
      (gb-date (bs/get-text this :GBSeq_update-date)))
    :version
    (fn [this]
      (Integer.
       (second
        (re-find #".+\.(\d+)"
                 (bs/get-text this :GBSeq_accession-version))))))
  bs/biosequenceDescription
  (assoc bs/default-biosequence-description
    :description
    (fn [this] (bs/get-text this :GBSeq_definition)))
  bs/Biosequence
  (assoc bs/default-biosequence-biosequence
    :bs-seq
    (fn [this]
      (vec (:sequence this)))
    :protein?
    (fn [this] (if (alphabet-is-protein? (bs/alphabet this))
                 true false))
    :alphabet
    (fn [this] (:alphabet this))
    :moltype
    (fn [this]
      (bs/get-text this :GBSeq_moltype))
    :keywords
    (fn [this] (bs/get-list this :GBSeq_keywords :GBKeyword
                            zf/text)))
  bs/biosequenceCitations
  (assoc bs/default-biosequence-citations
    :citations
    (fn [this]
      (map #(->genbankCitation (node %))
           (bs/get-list this :GBSeq_references :GBReference))))
  bs/biosequenceFeatures
  (assoc bs/default-biosequence-features
    :feature-seq
    (fn [this]
      (map #(->genbankFeature %)
           (:content (some #(if (= (:tag %) :GBSeq_feature-table)
                              %)
                           (:content (:src this))))))
    :filter-features
    (fn [this name]
      (filter #(= (bs/obj-type %) name)
              (bs/feature-seq this))))
  bs/biosequenceTaxonomies
  (assoc bs/default-biosequence-taxonomies
    :tax-refs (fn [this] (list (->genbankTaxRef (:src this))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IO
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankReader [strm alphabet]
  bs/biosequenceReader
  (biosequence-seq [this]
    (let [xml (parse (:strm this) :support-dtd false)]
      (->> (map (fn [x]
                  (vec x) ;; realising all the laziness
                  (->genbankSequence x (:alphabet this)))
                (filter #(= (:tag %) :GBSeq)
                        (:content xml)))
           (map #(assoc % :sequence
                        (bs/get-text this :GBSeq_sequence)))
           (bs/clean-sequences (:alphabet this)))))
  java.io.Closeable
  (close [this]
    (.close (:strm this))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankFile [file opts alphabet])

(extend genbankFile
  bs/biosequenceIO
  {:bs-reader
   (fn [this]
     (->genbankReader (apply bs/bioreader (bs/bs-path this)
                             (:opts this))
                      (:alphabet this)))}
  bs/biosequenceFile
  bs/default-biosequence-file)

(defn init-genbank-file
  "Initializes a genbankFile record."
  [file alphabet & opts]
  {:pre [(file? file)]}
  (->genbankFile file opts alphabet))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; string
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankString [str opts alphabet]
  bs/biosequenceIO
  (bs-reader [this]
    (->genbankReader (apply bs/bioreader (:str this)
                            (:opts this))
                     (:alphabet this))))

(defn init-genbank-string
  "Initializes a genbankString record."
  [str & {:keys [alphabet opts] :or {alphabet :iupacAminoAcids
                                     opts ()}}]
  (->genbankString str opts alphabet))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; connection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord genbankConnection [acc-list db retype alphabet opts]
  bs/biosequenceIO
  (bs-reader [this]
    (let [s (e-fetch (:acc-list this)
                        (:db this)
                        (condp = (:retype this)
                          :xml "gb" :fasta "fasta")
                        (condp = (:retype this)
                          :xml "xml" :fasta "text"))]
      (condp = (:retype this)
        :xml (->genbankReader (apply bs/bioreader s (:opts this))
                              (:alphabet this))
        :fasta (bs/init-fasta-reader
                (apply bs/bioreader s (:opts this))
                (:alphabet this))))))

(defn init-genbank-connection
  "Initializes a genbankConnection record."
  [accessions db format alphabet & opts]
  {:pre [(#{:xml :fasta} format)
         (#{:nucest :nuccore :nucgss :popset :protein} db)]}
  (->genbankConnection accessions db format alphabet opts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; web
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn genbank-search
  "Returns a non-lazy list of result ids from NCBI for a particular search term and
   database. Search term syntax is the same as is used at the NCBI. For example,
   to retreive all Schistosoma mansoni protein sequences the 'db' argument would
   be 'protein' and the search term 'txid6183[Organism:noexp]'. Database arguments
   are restricted to the following keyword arguments and any other value will cause
   an error:
   :protein    - a collection of sequences from several sources, including 
                 translations from annotated coding regions in GenBank, RefSeq 
                 and TPA, as well as records from SwissProt, PIR, PRF, and PDB.
   :nucest     - a collection of short single-read transcript sequences from GenBank.
   :nuccore    - a collection of sequences from several sources, including GenBank, 
                 RefSeq, TPA and PDB.
   :nucgss     - a collection of unannotated short single-read primarily genomic
                 sequences from GenBank including random survey sequences clone-end
                 sequences and exon-trapped sequences.
   :popset     - a set of DNA sequences that have been collected to analyse the 
                 evolutionary relatedness of a population. The population could 
                 originate from different members of the same species, or from 
                 organisms from different species. 

   Note that when retrieving popset entries multiple sequences are returned from a
   single accession number. Returns an empty list if no matches found."
  ([term db] (genbank-search term db 0 nil))
  ([term db restart key]
   {:pre [(#{:nucest :nuccore :nucgss :popset :protein} db)]}
   (e-search term db restart key)))
