package inu.timetable.enums;

public enum SubjectType {
    전심("전공심화"),
    전핵("전공핵심"), 
    심교("심화교양"),
    핵교("핵심교양"),
    일선("일반선택"),
    전기("전공기초"),
    기교("기초교양"),
    군사학("군사학"),
    교직("교직");

    
    private final String description;
    
    SubjectType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}