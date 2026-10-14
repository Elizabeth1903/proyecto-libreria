-- Base de datos de la libreria
-- Hecho para SQLite (tambien funciona en MySQL cambiando AUTOINCREMENT por AUTO_INCREMENT)

DROP TABLE IF EXISTS detalle_venta;
DROP TABLE IF EXISTS ventas;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS categorias;
DROP TABLE IF EXISTS proveedores;
DROP TABLE IF EXISTS clientes;

CREATE TABLE clientes (
    id_cliente INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre     VARCHAR(80) NOT NULL,
    dni        CHAR(8) UNIQUE,
    telefono   VARCHAR(15),
    correo     VARCHAR(80)
);

CREATE TABLE proveedores (
    id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre       VARCHAR(80) NOT NULL,
    ruc          CHAR(11) UNIQUE,
    telefono     VARCHAR(15),
    ciudad       VARCHAR(50)
);

CREATE TABLE categorias (
    id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre       VARCHAR(50) NOT NULL
);

CREATE TABLE productos (
    id_producto  INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre       VARCHAR(80) NOT NULL,
    precio       DECIMAL(8,2) NOT NULL,
    stock        INTEGER NOT NULL DEFAULT 0,
    id_categoria INTEGER NOT NULL,
    id_proveedor INTEGER NOT NULL,
    FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria),
    FOREIGN KEY (id_proveedor) REFERENCES proveedores(id_proveedor)
);

CREATE TABLE ventas (
    id_venta   INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha      DATE NOT NULL,
    id_cliente INTEGER NOT NULL,
    FOREIGN KEY (id_cliente) REFERENCES clientes(id_cliente)
);

-- Una venta puede tener varios productos, por eso se separa el detalle
CREATE TABLE detalle_venta (
    id_detalle      INTEGER PRIMARY KEY AUTOINCREMENT,
    id_venta        INTEGER NOT NULL,
    id_producto     INTEGER NOT NULL,
    cantidad        INTEGER NOT NULL,
    precio_unitario DECIMAL(8,2) NOT NULL,
    FOREIGN KEY (id_venta) REFERENCES ventas(id_venta),
    FOREIGN KEY (id_producto) REFERENCES productos(id_producto)
);
