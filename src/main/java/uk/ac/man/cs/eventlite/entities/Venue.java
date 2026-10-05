package uk.ac.man.cs.eventlite.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "venue")
public class Venue {

	@Id
	@GeneratedValue
	@Column
	private long id;

	@NotBlank(message = "The Venue cannot be empty.")
	@Size(max = 255, message = "The venue must have 255 characters or less.")
	@Column(length = 255)
	private String name;

	@Column
	@Positive(message = "must be a positive integer.")
	private int capacity;

	@NotBlank(message = "The Road cannot be empty")
	@Size(max = 299, message = "The road name must have 299 characters or less.")
	@Column(length = 299)
	private String road;

	@NotBlank(message = "The Postcode cannot be empty")
	@Column
	private String postcode;

	@Column
	private Double latitude;

	@Column
	private Double longitude;

	@OneToMany(mappedBy = "venue")
	private List<Event> events = new ArrayList<>();

	public Venue() {
	}

	public long getId() {
		return id;
	}

	@JsonIgnore
	public List<Event> getEvents(){
		return events;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public String getRoad() {
		return road;
	}

	public String getPostcode() {
		return postcode;
	}

	public void setRoad(String road) {
	    this.road = road;
	}

	public void setPostcode(String postcode) {
	    this.postcode = postcode;
	}

    public Double getLatitude() {
        return latitude;
    }
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    public Double getLongitude() {
        return longitude;
    }
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

	public void setAddress(String road, String postcode) {
		this.road = road;
		this.postcode = postcode;
	}
}
