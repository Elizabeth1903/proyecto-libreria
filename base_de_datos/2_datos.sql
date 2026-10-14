-- Datos de prueba para la libreria
-- Todos los datos son ficticios (DNI, RUC, telefonos y correos inventados)

INSERT INTO clientes (nombre, dni, telefono, correo) VALUES
('Maria Quispe Huaman',   '10000001', '900000001', 'maria.quispe@ejemplo.com'),
('Jose Flores Rojas',     '10000002', '900000002', 'jose.flores@ejemplo.com'),
('Lucia Mendoza Castro',  '10000003', '900000003', 'lucia.mendoza@ejemplo.com'),
('Carlos Ramirez Soto',   '10000004', '900000004', 'carlos.ramirez@ejemplo.com'),
('Ana Torres Vargas',     '10000005', '900000005', 'ana.torres@ejemplo.com'),
('Pedro Gutierrez Leon',  '10000006', '900000006', NULL);

INSERT INTO proveedores (nombre, ruc, telefono, ciudad) VALUES
('Distribuidora Escolar SAC', '20000000001', '010000001', 'Lima'),
('Papelera del Sur EIRL',     '20000000002', '010000002', 'Arequipa'),
('Editorial Andina SA',       '20000000003', '010000003', 'Lima'),
('Importaciones Tinta SAC',   '20000000004', '010000004', 'Trujillo');

INSERT INTO categorias (nombre) VALUES
('Cuadernos'),
('Utiles de escritura'),
('Libros'),
('Papeleria'),
('Arte');

INSERT INTO productos (nombre, precio, stock, id_categoria, id_proveedor) VALUES
('Cuaderno cuadriculado A4',     6.50, 120, 1, 1),
('Cuaderno rayado A5',           4.00,  80, 1, 1),
('Lapicero azul',                1.20, 300, 2, 4),
('Lapiz 2B',                     1.00, 250, 2, 4),
('Resaltador amarillo',          2.50,  60, 2, 4),
('Borrador blanco',              0.80,   4, 2, 1),
('Papel bond A4 x500',          14.90,  40, 4, 2),
('Folder manila',                0.50, 200, 4, 2),
('Diccionario escolar',         25.00,  15, 3, 3),
('Libro de matematica 5to',     48.00,   3, 3, 3),
('Temperas x6',                  7.50,  25, 5, 1),
('Plumones x12',                12.00,  30, 5, 4);

INSERT INTO ventas (fecha, id_cliente) VALUES
('2025-03-03', 1),
('2025-03-03', 2),
('2025-03-05', 3),
('2025-03-10', 1),
('2025-03-15', 4),
('2025-04-02', 2),
('2025-04-08', 5),
('2025-04-20', 1),
('2025-05-06', 3),
('2025-05-12', 4);

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario) VALUES
(1,  1, 5,  6.50),
(1,  3, 10, 1.20),
(1,  4, 5,  1.00),
(2,  9, 1, 25.00),
(2,  7, 2, 14.90),
(3, 10, 1, 48.00),
(3,  1, 3,  6.50),
(4, 12, 2, 12.00),
(4, 11, 1,  7.50),
(5,  3, 20, 1.20),
(5,  8, 30, 0.50),
(6,  2, 4,  4.00),
(6,  5, 3,  2.50),
(7,  7, 5, 14.90),
(7,  3, 12, 1.20),
(8,  1, 2,  6.50),
(8,  6, 4,  0.80),
(9,  9, 2, 25.00),
(9, 12, 1, 12.00),
(10, 4, 15, 1.00),
(10, 1, 6,  6.50);
