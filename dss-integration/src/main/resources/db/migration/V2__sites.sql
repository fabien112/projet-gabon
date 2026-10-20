-- Sites : chaque caméra est rattachée à un site (remplace l'ancienne colonne texte camera.site)

CREATE TABLE IF NOT EXISTS site (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_site_name ON site (name);

ALTER TABLE camera ADD COLUMN IF NOT EXISTS site_id BIGINT REFERENCES site(id);
CREATE INDEX IF NOT EXISTS idx_camera_site ON camera (site_id);

INSERT INTO site (name)
SELECT DISTINCT TRIM(site) FROM camera
WHERE site IS NOT NULL AND TRIM(site) <> ''
ON CONFLICT (name) DO NOTHING;

UPDATE camera c
SET site_id = s.id
FROM site s
WHERE c.site_id IS NULL
  AND c.site IS NOT NULL
  AND LOWER(s.name) = LOWER(TRIM(c.site));

ALTER TABLE camera DROP COLUMN IF EXISTS site;
