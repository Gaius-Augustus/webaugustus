package webaugustus

class Training extends AbstractWebAugustusDomainClass {

    static searchable = true
    String id 
    static mapping ={
     id generator:'uuid', sqlType: "varchar(36)"
    }
    String email_adress
    Boolean agree_email = false
    Boolean agree_nonhuman = false
    String project_name
    String genome_file
    String genome_ftp_link
    String est_file
    String est_ftp_link
    String job_id // SGE Job ID will be determined by controller
    /**
     * SGE job status
     * 0 - Job preparation
     * 1 - Job submitted
     * 2 - waiting for execution
     * 3 - computing
     * 4 - finished
     */
    String job_status // SGE job status will be determined by controller
    /**
     * 0 - OK
     * 5 - error
     */
    String job_error
    String protein_file
    String protein_ftp_link
    String struct_file
    //generate a random (and unique) string for links to results here
    private static String validChars ="ABCDEFGHJKLMNPQRSTUVWXYZ123456789_abcdefghijkmnpqrstuvqxyz"
    private int IDlength=8
    int maxIndex = validChars.length()
    def rnd = new Random()
    String bef_accession_id = (1..IDlength).sum{ 
       validChars[ rnd.nextInt(maxIndex) ] 
    } 
    String accession_id = "train${bef_accession_id}"

    // checksum columns in database, values will be determined by controller
    String genome_cksum = 0
    String est_cksum = 0
    String protein_cksum = 0
    String struct_cksum = 0
    String genome_size = 0
    String est_size = 0
    String protein_size = 0
    String struct_size = 0
    Date dateCreated
    String results_urls
    String message
    String old_url
    // flags for emtpy fields in form after redirect
    Boolean warn
    Boolean has_genome_file
    Boolean has_est_file
    Boolean has_protein_file
    Boolean has_struct_file
    static constraints ={
       // accession_id(unique:false) // may (unlikely) cause problems if the grails database ever gets lost.
        email_adress(email:true,blank:true,nullable:true)
        agree_email(validator: {val, obj ->
                       if(obj.email_adress != null && obj.agree_email!=true){
                               return 'not_agreed'
                   }
                   })
         agree_nonhuman(validator: { val, obj ->
               if(obj.agree_nonhuman == false){
                         return 'not_agreed'
           }
     })
        project_name(blank:false, unique:false, maxSize:30)
        genome_file(nullable:true, blank:true, validator: { val, obj ->
             if (obj.genome_file == null && obj.genome_ftp_link == null) {
                 return 'no_genome_file'
             } else if (!(obj.genome_ftp_link == null) && !(obj.genome_file == null)) {
                 return 'not_both'
             } else if ((obj.est_file == null) && (obj.est_ftp_link == null) && (obj.struct_file == null) && (obj.protein_file == null) && (obj.protein_ftp_link == null)) {
                 return 'at_least_one'
             } else if (!(obj.protein_file == null) && !(obj.struct_file == null)) {
                 return 'not_struct'
             } else if (!(obj.protein_ftp_link == null) && !(obj.struct_file == null)) {
                 return 'not_struct'
             } else if (obj.genome_ftp_link =~ /dropbox/) {
             return 'no_dropbox'
         }
        })
        genome_ftp_link(nullable:true, blank:true, url:true)
        est_file(nullable:true, blank:true, validator: { val, obj ->
            if (!(obj.est_file == null) && !(obj.est_ftp_link == null)) {
                 return 'not_both'
            } else if (obj.est_ftp_link =~ /dropbox/) {
                 return 'no_dropbox'
            }


        })
        est_ftp_link(nullable:true, blank:true, url:true)
        protein_file(nullable:true, blank:true, validator: { val, obj ->
            if (!(obj.protein_file == null) && !(obj.protein_ftp_link == null)) {
                 return 'not_both'
            }else if (obj.protein_ftp_link =~ /dropbox/) {
                 return 'no_dropbox'
             }
        })
        struct_file(nullable:true, blank:true)
        protein_ftp_link(nullable:true, blank:true, url:true)
        genome_cksum(nullable:true)
        est_cksum(nullable:true)
        protein_cksum(nullable:true)
        struct_cksum(nullable:true)
        genome_size(nullable:true)
        est_size(nullable:true)
        protein_size(nullable:true)
        struct_size(nullable:true)
        job_id(nullable:true)
        job_status(nullable:true)
        job_error(nullable:true)
        dateCreated(nullable:false) // or  dateCreated [:] 
        old_url(nullable:true)
        results_urls(maxSize:1000000000, nullable:true)
        message(maxSize:1000000000, nullable:true) 
        warn(nullable:true)
        has_genome_file(nullable:true)
        has_est_file(nullable:true)
        has_protein_file(nullable:true)
        has_struct_file(nullable:true)
     }
}
