package org.example.techstarsscraper.model;
import jakarta.persistence.*;
import lombok.*;

import java.util.Map;

@Entity
@Table(name = "jobs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="job_page_url", unique=true, nullable=false)
    private String jobPageUrl;

    @Column(name="position_name")
    private String positionName;

    @Column(name="organization_url")
    private String organizationUrl;

    @Column(name="logo_url")
    private String logoUrl;

    @Column(name="organization_title")
    private String organizationTitle;

    @Column(name="labor_function")
    private String laborFunction;

    @Column(name="location_raw")
    private String locationRaw;

    @Column(name="location_city")
    private String locationCity;

    @Column(name="location_state")
    private String locationState;

    @Column(name="location_country")
    private String locationCountry;

    @Column(name="posted_date_unix")
    private Long postedDateUnix;

    @Column(name="description_html", columnDefinition = "text")
    private String descriptionHtml;

    @Column(name="tags", length=1000)
    private String tags;
}
